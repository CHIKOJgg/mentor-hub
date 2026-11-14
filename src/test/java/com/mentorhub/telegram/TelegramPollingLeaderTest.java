package com.mentorhub.telegram;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Testcontainers(disabledWithoutDocker = true)
class TelegramPollingLeaderTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("telegram_polling_lock_test")
            .withUsername("telegram_test")
            .withPassword("telegram_test_only");

    @Test
    void onlyOneDatabaseSessionCanOwnTelegramPollingLock() throws Exception {
        TelegramPollingLeader leader = new TelegramPollingLeader(mock(DataSource.class), null);

        try (Connection firstInstance = connect(); Connection secondInstance = connect()) {
            assertThat(leader.tryAcquireLock(firstInstance)).isTrue();
            assertThat(leader.tryAcquireLock(secondInstance)).isFalse();

            leader.releaseLock(firstInstance);

            assertThat(leader.tryAcquireLock(secondInstance)).isTrue();
            leader.releaseLock(secondInstance);
        }
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
