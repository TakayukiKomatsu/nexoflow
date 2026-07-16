package com.srm.creditengine.identity.application;

import com.srm.creditengine.shared.api.LoginRateLimitedException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 60;

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String email) {
        String key = email.toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        attempts.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.startedAt().plusSeconds(WINDOW_SECONDS))) {
                return new Window(now, 1);
            }
            if (current.count() >= MAX_ATTEMPTS) {
                throw new LoginRateLimitedException();
            }
            return new Window(current.startedAt(), current.count() + 1);
        });
    }

    private record Window(Instant startedAt, int count) {}
}
