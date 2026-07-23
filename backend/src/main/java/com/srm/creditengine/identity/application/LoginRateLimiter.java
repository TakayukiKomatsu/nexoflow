package com.srm.creditengine.identity.application;

import com.srm.creditengine.shared.api.LoginRateLimitedException;
import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 60;
    private static final int DEFAULT_MAX_BUCKETS = 10_000;

    private final Clock clock;
    private final int maxBuckets;
    private final LinkedHashMap<Key, Window> attempts = new LinkedHashMap<>(16, 0.75f, true);

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
        Key key = new Key(normalizeEmail(email), normalizeSource(source));
        Instant now = clock.instant();
        evictExpired(now);
        Window current = attempts.get(key);
        if (current == null) {
            attempts.put(key, new Window(now, 1));
            evictEldestBeyondCapacity();
            return;
        }
        if (current.count() >= MAX_ATTEMPTS) throw new LoginRateLimitedException();
        attempts.put(key, new Window(current.startedAt(), current.count() + 1));
    }

    public synchronized void successful(String email, String source) {
        attempts.remove(new Key(normalizeEmail(email), normalizeSource(source)));
    }

    private void evictExpired(Instant now) {
        attempts.entrySet().removeIf(entry ->
                !now.isBefore(entry.getValue().startedAt().plusSeconds(WINDOW_SECONDS)));
    }

    private void evictEldestBeyondCapacity() {
        if (attempts.size() > maxBuckets) {
            Iterator<Map.Entry<Key, Window>> entries = attempts.entrySet().iterator();
            entries.next();
            entries.remove();
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

    private record Key(String email, String source) {}
    private record Window(Instant startedAt, int count) {}
}
