package com.mentorhub.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

@Component
public final class AdminLoginRateLimiter {
    private static final int MAX_ATTEMPTS_PER_WINDOW = 10;
    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private static final long WINDOW_MILLIS = 60_000;
    private static final long ENTRY_TTL_MILLIS = WINDOW_MILLIS * 2;

    private final ConcurrentHashMap<String, AttemptWindow> clients = new ConcurrentHashMap<>();
    private final AtomicLong nextCleanupAt = new AtomicLong();
    private final LongSupplier clockMillis;

    public AdminLoginRateLimiter() {
        this(System::currentTimeMillis);
    }

    AdminLoginRateLimiter(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    public boolean tryAcquire(String clientAddress) {
        long now = clockMillis.getAsLong();
        cleanupExpiredEntries(now);
        String client = clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress;

        if (!clients.containsKey(client) && clients.size() >= MAX_TRACKED_CLIENTS) return false;

        AtomicBoolean allowed = new AtomicBoolean();
        clients.compute(client, (key, current) -> {
            if (current == null || now - current.windowStartedAt >= WINDOW_MILLIS) {
                allowed.set(true);
                return new AttemptWindow(now, 1);
            }
            if (current.attempts >= MAX_ATTEMPTS_PER_WINDOW) {
                allowed.set(false);
                return current;
            }
            allowed.set(true);
            return new AttemptWindow(current.windowStartedAt, current.attempts + 1);
        });
        return allowed.get();
    }

    private void cleanupExpiredEntries(long now) {
        long scheduledAt = nextCleanupAt.get();
        if (now < scheduledAt || !nextCleanupAt.compareAndSet(scheduledAt, now + WINDOW_MILLIS)) return;
        clients.entrySet().removeIf(entry -> now - entry.getValue().windowStartedAt >= ENTRY_TTL_MILLIS);
    }

    private record AttemptWindow(long windowStartedAt, int attempts) {}
}
