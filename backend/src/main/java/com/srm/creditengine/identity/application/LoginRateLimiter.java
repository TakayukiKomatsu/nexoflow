package com.srm.creditengine.identity.application;

import com.srm.creditengine.shared.api.LoginRateLimitedException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
    private static final int MAX_ACCOUNT_ATTEMPTS = 5;
    private static final int MAX_SOURCE_ATTEMPTS = 20;
    private static final long WINDOW_SECONDS = 60;
    private static final int DEFAULT_MAX_BUCKETS = 10_000;

    private final Clock clock;
    private final int maxBuckets;
    private final Map<String, Window> accountAttempts = new HashMap<>();
    private final Map<String, Window> sourceAttempts = new HashMap<>();

    public LoginRateLimiter(Clock clock) {
        this(clock, DEFAULT_MAX_BUCKETS);
    }

    @Autowired
    public LoginRateLimiter(
            Clock clock,
            @Value("${srm.security.login-rate-limit.max-buckets:10000}") int maxBuckets) {
        if (maxBuckets < 1) throw new IllegalArgumentException("maxBuckets must be positive");
        this.clock = clock;
        this.maxBuckets = maxBuckets;
    }

    public synchronized void check(String email, String source) {
        String accountKey = normalizeEmail(email);
        String sourceKey = normalizeSource(source);
        Instant now = clock.instant();
        evictExpired(accountAttempts, now);
        evictExpired(sourceAttempts, now);
        Window accountWindow = accountAttempts.get(accountKey);
        Window sourceWindow = sourceAttempts.get(sourceKey);
        if (accountWindow != null && accountWindow.count() >= MAX_ACCOUNT_ATTEMPTS) {
            throw new LoginRateLimitedException();
        }
        if (sourceWindow != null && sourceWindow.count() >= MAX_SOURCE_ATTEMPTS) {
            throw new LoginRateLimitedException();
        }
        requireCapacity(accountAttempts, accountKey);
        requireCapacity(sourceAttempts, sourceKey);
        record(accountAttempts, accountKey, accountWindow, now);
        record(sourceAttempts, sourceKey, sourceWindow, now);
    }

    public synchronized void successful(String email, String source) {
        accountAttempts.remove(normalizeEmail(email));
        String sourceKey = normalizeSource(source);
        Window sourceWindow = sourceAttempts.get(sourceKey);
        if (sourceWindow == null || sourceWindow.count() == 1) {
            sourceAttempts.remove(sourceKey);
        } else {
            sourceAttempts.put(
                    sourceKey, new Window(sourceWindow.startedAt(), sourceWindow.count() - 1));
        }
    }

    private static void record(
            Map<String, Window> windows, String key, Window current, Instant now) {
        windows.put(
                key,
                current == null
                        ? new Window(now, 1)
                        : new Window(current.startedAt(), current.count() + 1));
    }

    private static void evictExpired(Map<String, Window> windows, Instant now) {
        windows.entrySet().removeIf(entry ->
                !now.isBefore(entry.getValue().startedAt().plusSeconds(WINDOW_SECONDS)));
    }

    private void requireCapacity(Map<String, Window> windows, String key) {
        if (!windows.containsKey(key) && windows.size() >= maxBuckets) {
            throw new LoginRateLimitedException();
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSource(String source) {
        if (source == null || source.isBlank()) return "UNKNOWN";
        String normalized = source.strip().toLowerCase(Locale.ROOT);
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private record Window(Instant startedAt, int count) {}
}
