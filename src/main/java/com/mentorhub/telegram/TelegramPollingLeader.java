package com.mentorhub.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.ExponentialBackOff;
import org.telegram.telegrambots.meta.TelegramUrl;
import org.telegram.telegrambots.meta.api.methods.updates.GetUpdates;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps Telegram long polling to one application instance during rolling deployments.
 * The database session lock is held until the poller is stopped and its current request exits.
 */
@Slf4j
@Component
@ConditionalOnExpression("'${telegram.bot.enabled:false}' == 'true' && '${telegram.bot.mode:long-polling}' == 'long-polling' && !'${telegram.bot.token:}'.isEmpty() && !'${telegram.bot.token:}'.startsWith('mock')")
public class TelegramPollingLeader {

    private static final int LOCK_NAMESPACE = 1_296_580_914;
    private static final int LOCK_ID = 1_413_952_077;
    private static final int STARTUP_GRACE_SECONDS = 10;
    private static final int LOCK_RETRY_SECONDS = 3;
    private static final int TELEGRAM_LONG_POLL_SECONDS = 5;
    private static final int HTTP_CALL_TIMEOUT_SECONDS = 8;

    private final DataSource dataSource;
    private final MentorBotConsumer bot;
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final Object shutdownMonitor = new Object();
    private volatile Thread worker;

    @Value("${telegram.bot.token:}")
    private String botToken;

    public TelegramPollingLeader(DataSource dataSource, MentorBotConsumer bot) {
        this.dataSource = dataSource;
        this.bot = bot;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void startAfterApplicationReady() {
        if (worker != null) {
            return;
        }
        worker = Thread.ofVirtual()
                .name("telegram-polling-leader")
                .start(this::runLeaderLoop);
    }

    @PreDestroy
    public void stop() {
        stopping.set(true);
        Thread currentWorker = worker;
        if (currentWorker == null) {
            return;
        }
        synchronized (shutdownMonitor) {
            shutdownMonitor.notifyAll();
        }
        try {
            currentWorker.join(TimeUnit.SECONDS.toMillis(15));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void runLeaderLoop() {
        if (!sleep(STARTUP_GRACE_SECONDS)) {
            return;
        }

        boolean waitingForAnotherInstance = false;
        while (!stopping.get()) {
            Connection lockConnection = null;
            ScheduledExecutorService pollingExecutor = null;
            TelegramBotsLongPollingApplication pollingApplication = null;
            boolean lockAcquired = false;

            try {
                lockConnection = dataSource.getConnection();
                lockAcquired = tryAcquireLock(lockConnection);
                if (!lockAcquired) {
                    if (!waitingForAnotherInstance) {
                        log.info("Another application instance owns Telegram polling; waiting for its database lock");
                        waitingForAnotherInstance = true;
                    }
                    closeQuietly(lockConnection);
                    lockConnection = null;
                    sleep(LOCK_RETRY_SECONDS);
                    continue;
                }

                ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(task, "telegram-long-polling");
                    thread.setDaemon(true);
                    return thread;
                });
                pollingExecutor = executor;
                OkHttpClient telegramHttpClient = new OkHttpClient.Builder()
                        .callTimeout(HTTP_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .build();
                pollingApplication = new TelegramBotsLongPollingApplication(
                        ObjectMapper::new,
                        () -> telegramHttpClient,
                        () -> executor,
                        ExponentialBackOff::new
                );

                pollingApplication.registerBot(
                        botToken,
                        () -> TelegramUrl.DEFAULT_URL,
                        this::createGetUpdatesRequest,
                        bot.getUpdatesConsumer()
                );
                log.info("Telegram long polling started after acquiring the application-wide database lock");
                waitingForAnotherInstance = false;

                while (!stopping.get() && lockConnection.isValid(2)) {
                    sleep(2);
                }
                if (!stopping.get()) {
                    log.warn("Telegram polling leadership database connection was lost; stopping this poller");
                }
            } catch (SQLException ex) {
                log.warn("Could not maintain Telegram polling leadership: {}", ex.getMessage());
            } catch (Exception ex) {
                log.warn("Telegram polling could not start or continue: {}", ex.getMessage());
            } finally {
                stopPollingExecutor(pollingExecutor);
                closePollingApplication(pollingApplication);
                if (lockAcquired && lockConnection != null) {
                    releaseLock(lockConnection);
                }
                closeQuietly(lockConnection);
            }

            if (!stopping.get()) {
                sleep(LOCK_RETRY_SECONDS);
            }
        }
    }

    private GetUpdates createGetUpdatesRequest(Integer lastUpdateId) {
        return GetUpdates.builder()
                .limit(100)
                .timeout(TELEGRAM_LONG_POLL_SECONDS)
                .offset(lastUpdateId + 1)
                .build();
    }

    boolean tryAcquireLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?, ?)")) {
            statement.setInt(1, LOCK_NAMESPACE);
            statement.setInt(2, LOCK_ID);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    void releaseLock(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?, ?)")) {
            statement.setInt(1, LOCK_NAMESPACE);
            statement.setInt(2, LOCK_ID);
            statement.execute();
        } catch (SQLException ex) {
            log.debug("Telegram polling database lock will be released when its connection closes: {}", ex.getMessage());
        }
    }

    private void closePollingApplication(TelegramBotsLongPollingApplication application) {
        if (application == null) {
            return;
        }
        try {
            application.close();
        } catch (Exception ex) {
            log.debug("Telegram polling session close failed: {}", ex.getMessage());
        }
    }

    private void stopPollingExecutor(ScheduledExecutorService executor) {
        if (executor == null) {
            return;
        }
        boolean interrupted = Thread.interrupted();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(HTTP_CALL_TIMEOUT_SECONDS + 1L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            interrupted = true;
            executor.shutdownNow();
            try {
                executor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean sleep(int seconds) {
        synchronized (shutdownMonitor) {
            if (stopping.get()) {
                return false;
            }
            try {
                TimeUnit.SECONDS.timedWait(shutdownMonitor, seconds);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
            return !stopping.get();
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            log.debug("Telegram polling lock connection close failed: {}", ex.getMessage());
        }
    }
}
