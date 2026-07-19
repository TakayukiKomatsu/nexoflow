package com.srm.creditengine.currency.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpFxSynchronizationServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-15T12:00:00Z");
    private static final String SUCCESS_BODY =
            "{\"rate\":\"5.20\",\"observedAt\":\"2030-01-15T12:00:00Z\",\"source\":\"mock\"}";

    @Test
    void resourceAccessFailuresRetryThreeTimesWithExponentialDelays() {
        CurrencyService currency = mock(CurrencyService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        for (int attempt = 0; attempt < 3; attempt++) {
            server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                    .andRespond(request -> {
                        throw new org.springframework.web.client.ResourceAccessException("offline");
                    });
        }
        List<Duration> waits = new ArrayList<>();
        var service = service(currency, builder, Clock.fixed(NOW, ZoneOffset.UTC), waits);

        assertThatThrownBy(() -> service.synchronize("USD", "BRL", "admin@srm.local"))
                .isInstanceOf(FxProviderUnavailableException.class);

        assertThat(waits).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200));
        server.verify();
    }

    @Test
    void serviceUnavailableRetriesThreeTimes() {
        CurrencyService currency = mock(CurrencyService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        for (int attempt = 0; attempt < 3; attempt++) {
            server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                            .withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }
        List<Duration> waits = new ArrayList<>();
        var registry = new SimpleMeterRegistry();
        var service = new HttpFxSynchronizationService(currency, builder.baseUrl("http://fx").build(),
                Clock.fixed(NOW, ZoneOffset.UTC), registry, FxRetryDelay.exponential(() -> 0.5d), waits::add);

        assertThatThrownBy(() -> service.synchronize("USD", "BRL", "admin@srm.local"))
                .isInstanceOf(FxProviderUnavailableException.class);

        assertThat(waits).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200));
        assertThat(registry.find("srm_fx_provider_attempt_duration_seconds")
                .tag("result", "TRANSIENT_FAILURE").timer().count()).isEqualTo(3);
        server.verify();
    }

    @Test
    void tooManyRequestsRetriesThreeTimesWithExponentialDelays() {
        CurrencyService currency = mock(CurrencyService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        for (int attempt = 0; attempt < 3; attempt++) {
            server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                            .withStatus(HttpStatus.TOO_MANY_REQUESTS));
        }
        List<Duration> waits = new ArrayList<>();
        var service = service(currency, builder, Clock.fixed(NOW, ZoneOffset.UTC), waits);

        assertThatThrownBy(() -> service.synchronize("USD", "BRL", "admin@srm.local"))
                .isInstanceOf(FxProviderUnavailableException.class);

        assertThat(waits).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200));
        server.verify();
    }

    @Test
    void badRequestFailsAfterOneAttemptWithoutDelay() {
        CurrencyService currency = mock(CurrencyService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.BAD_REQUEST));
        List<Duration> waits = new ArrayList<>();
        var registry = new SimpleMeterRegistry();
        var service = new HttpFxSynchronizationService(currency, builder.baseUrl("http://fx").build(),
                Clock.fixed(NOW, ZoneOffset.UTC), registry, FxRetryDelay.exponential(() -> 0.5d), waits::add);

        assertThatThrownBy(() -> service.synchronize("USD", "BRL", "admin@srm.local"))
                .isInstanceOf(FxProviderUnavailableException.class);

        assertThat(waits).isEmpty();
        assertThat(registry.find("srm_fx_provider_attempt_duration_seconds")
                .tag("result", "PERMANENT_FAILURE").timer().count()).isEqualTo(1);
        server.verify();
    }

    @Test
    void invalidSuccessfulBodyFailsAfterOneAttemptWithoutDelay() {
        CurrencyService currency = mock(CurrencyService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withSuccess("{}", org.springframework.http.MediaType.APPLICATION_JSON));
        List<Duration> waits = new ArrayList<>();
        var registry = new SimpleMeterRegistry();
        var service = new HttpFxSynchronizationService(currency, builder.baseUrl("http://fx").build(),
                Clock.fixed(NOW, ZoneOffset.UTC), registry, FxRetryDelay.exponential(() -> 0.5d), waits::add);

        assertThatThrownBy(() -> service.synchronize("USD", "BRL", "admin@srm.local"))
                .isInstanceOf(FxProviderUnavailableException.class);

        assertThat(waits).isEmpty();
        assertThat(registry.find("srm_fx_provider_attempt_duration_seconds")
                .tag("result", "PERMANENT_FAILURE").timer().count()).isEqualTo(1);
        server.verify();
    }

    @Test
    void exhaustedTransientFailureOpensCircuitForExactlyThirtySeconds() {
        CurrencyService currency = mock(CurrencyService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        for (int attempt = 0; attempt < 3; attempt++) {
            server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                            .withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        SUCCESS_BODY, org.springframework.http.MediaType.APPLICATION_JSON));
        MutableClock clock = new MutableClock(NOW);
        var service = service(currency, builder, clock, new ArrayList<>());

        assertThatThrownBy(() -> service.synchronize("USD", "BRL", "admin@srm.local"))
                .isInstanceOf(FxProviderUnavailableException.class);
        clock.advance(Duration.ofSeconds(29));
        assertThatThrownBy(() -> service.synchronize("USD", "BRL", "admin@srm.local"))
                .isInstanceOf(FxProviderUnavailableException.class);
        clock.advance(Duration.ofSeconds(1));

        assertThat(service.synchronize("USD", "BRL", "admin@srm.local").rate()).isEqualByComparingTo("5.20");
        server.verify();
    }

    @Test
    void slowerConcurrentSuccessCannotCloseCircuitOpenedByExhaustedFailure() throws Exception {
        CurrencyService currency = mock(CurrencyService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CountDownLatch slowAttemptStarted = new CountDownLatch(1);
        CountDownLatch releaseSlowAttempt = new CountDownLatch(1);
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .requestTo("http://fx/api/v1/rates/USD-BRL"))
                .andRespond(request -> {
                    slowAttemptStarted.countDown();
                    try {
                        if (!releaseSlowAttempt.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("Timed out waiting to release slow provider response");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(exception);
                    }
                    return org.springframework.test.web.client.response.MockRestResponseCreators
                            .withSuccess(SUCCESS_BODY, org.springframework.http.MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });
        for (int attempt = 0; attempt < 3; attempt++) {
            server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers
                            .requestTo("http://fx/api/v1/rates/USD-USD"))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                            .withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }
        var service = service(currency, builder, Clock.fixed(NOW, ZoneOffset.UTC), new ArrayList<>());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var slowSuccess = executor.submit(
                    () -> service.synchronize("USD", "BRL", "admin@srm.local"));
            assertThat(slowAttemptStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.synchronize("USD", "USD", "admin@srm.local"))
                    .isInstanceOf(FxProviderUnavailableException.class);
            releaseSlowAttempt.countDown();
            assertThat(slowSuccess.get(5, TimeUnit.SECONDS).rate()).isEqualByComparingTo("5.20");

            assertThatThrownBy(() -> service.synchronize("BRL", "USD", "admin@srm.local"))
                    .isInstanceOf(FxProviderUnavailableException.class);
            server.verify();
        } finally {
            releaseSlowAttempt.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void missingProviderSourceUsesAndReturnsThePersistedFallbackSource() {
        CurrencyService currency = mock(CurrencyService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"rate\":\"5.20\",\"observedAt\":\"2030-01-15T12:00:00Z\"}",
                        org.springframework.http.MediaType.APPLICATION_JSON));
        var service = service(currency, builder, Clock.fixed(NOW, ZoneOffset.UTC), new ArrayList<>());

        var observed = service.synchronize("USD", "BRL", "admin@srm.local");

        assertThat(observed.source()).isEqualTo("HTTP_PROVIDER");
        verify(currency).recordObservation("USD", "BRL", new BigDecimal("5.20"), "HTTP_PROVIDER",
                NOW, "admin@srm.local");
        server.verify();
    }

    private static HttpFxSynchronizationService service(
            CurrencyService currency, RestClient.Builder builder, Clock clock, List<Duration> waits) {
        return new HttpFxSynchronizationService(currency, builder.baseUrl("http://fx").build(), clock,
                new SimpleMeterRegistry(), FxRetryDelay.exponential(() -> 0.5d), waits::add);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
