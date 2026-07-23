package com.srm.creditengine.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.srm.creditengine.currency.domain.SupportedCurrency;
import com.srm.creditengine.currency.domain.UnsupportedCurrencyException;
import com.srm.creditengine.identity.application.LoginRateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RiskBoundaryTest {
    @Test
    void rejectsNullCurrencyAndMalformedDecimalStrings() {
        assertThatThrownBy(() -> SupportedCurrency.require(null))
                .isInstanceOf(UnsupportedCurrencyException.class);
        assertThatThrownBy(() -> DecimalString.from(JsonNodeFactory.instance.textNode("not-a-decimal")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Financial values must be valid decimal strings");
        assertThatThrownBy(() -> DecimalString.from(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Financial values must be decimal strings");
    }

    @Test
    void replacesMissingCorrelationIdWithASafeGeneratedIdentifier() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        new CorrelationIdFilter(new com.srm.creditengine.shared.runtime.SafeOperationalLogger())
                .doFilter(request, response, (ignoredRequest, ignoredResponse) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).matches("[0-9a-f-]{36}");
    }

    @Test
    void startsANewLoginWindowAtTheExactBoundary() {
        MutableClock clock = new MutableClock(Instant.parse("2030-01-15T12:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.check("operator@srm.local", "192.0.2.10");
        }
        assertThatThrownBy(() -> limiter.check("operator@srm.local", "192.0.2.10"))
                .isInstanceOf(LoginRateLimitedException.class);

        clock.advanceSeconds(60);
        limiter.check("operator@srm.local", "192.0.2.10");
    }

    @Test
    void loginLimiterEvictsTheLeastRecentBucketAtItsConfiguredCapacity() {
        MutableClock clock = new MutableClock(Instant.parse("2030-01-15T12:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(clock, 2);
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.check("oldest@srm.local", "192.0.2.10");
        }
        assertThatThrownBy(() -> limiter.check("oldest@srm.local", "192.0.2.10"))
                .isInstanceOf(LoginRateLimitedException.class);

        limiter.check("second@srm.local", "192.0.2.11");
        limiter.check("third@srm.local", "192.0.2.12");

        limiter.check("oldest@srm.local", "192.0.2.10");
    }

    @Test
    void successfulLoginClearsOnlyThatIdentityAndSourceBucket() {
        MutableClock clock = new MutableClock(Instant.parse("2030-01-15T12:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.check("operator@srm.local", "192.0.2.10");
        }

        limiter.successful("operator@srm.local", "192.0.2.10");

        limiter.check("operator@srm.local", "192.0.2.10");
    }

    @Test
    void loginLimiterRejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> new LoginRateLimiter(Clock.systemUTC(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxBuckets must be positive");
    }

    @Test
    void loginLimiterCanonicalizesAbsentIdentityAndSourceValues() {
        LoginRateLimiter limiter = new LoginRateLimiter(Clock.systemUTC());
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.check(null, attempt % 2 == 0 ? null : "  ");
        }

        assertThatThrownBy(() -> limiter.check(null, ""))
                .isInstanceOf(LoginRateLimitedException.class);
    }

    @Test
    void loginLimiterBoundsLongSourceIdentifiersBeforeBucketing() {
        LoginRateLimiter limiter = new LoginRateLimiter(Clock.systemUTC());
        String prefix = "x".repeat(64);
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.check("operator@srm.local", prefix + "first-suffix");
        }

        assertThatThrownBy(() -> limiter.check("operator@srm.local", prefix + "second-suffix"))
                .isInstanceOf(LoginRateLimitedException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
