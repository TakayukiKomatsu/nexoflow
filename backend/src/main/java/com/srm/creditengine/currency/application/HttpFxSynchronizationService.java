package com.srm.creditengine.currency.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import com.srm.creditengine.shared.runtime.FinancialTelemetry;

/** HTTP-only provider adapter. Retry and circuit behavior is deliberately confined here. */
@Service
class HttpFxSynchronizationService implements FxSynchronizationService {
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration CIRCUIT_OPEN_FOR = Duration.ofSeconds(30);
    private final CurrencyService currency;
    private final RestClient client;
    private final Clock clock;
    private final Counter requests;
    private final Counter failures;
    private final FxRetryDelay retryDelay;
    private final FxRetrySleeper sleeper;
    private final FinancialTelemetry telemetry;
    private volatile Instant circuitOpenUntil = Instant.EPOCH;

    @Autowired
    HttpFxSynchronizationService(CurrencyService currency, RestClient.Builder builder, Clock clock,
            MeterRegistry registry, @Value("${srm.fx-provider.base-url:http://localhost:8090}") String baseUrl) {
        this(currency, configuredClient(builder, baseUrl), clock, registry,
                FxRetryDelay.exponential(ThreadLocalRandom.current()::nextDouble),
                FxRetrySleeper.system(), new FinancialTelemetry(registry));
    }

    HttpFxSynchronizationService(CurrencyService currency, RestClient client, Clock clock, MeterRegistry registry) {
        this(currency, client, clock, registry, FxRetryDelay.exponential(ThreadLocalRandom.current()::nextDouble),
                FxRetrySleeper.system(), new FinancialTelemetry(registry));
    }

    HttpFxSynchronizationService(CurrencyService currency, RestClient client, Clock clock, MeterRegistry registry,
            FxRetryDelay retryDelay, FxRetrySleeper sleeper) {
        this(currency, client, clock, registry, retryDelay, sleeper, new FinancialTelemetry(registry));
    }

    HttpFxSynchronizationService(CurrencyService currency, RestClient client, Clock clock, MeterRegistry registry,
            FxRetryDelay retryDelay, FxRetrySleeper sleeper, FinancialTelemetry telemetry) {
        this.currency = currency;
        this.client = client;
        this.clock = clock;
        this.requests = registry.counter("srm.fx.provider.requests");
        this.failures = registry.counter("srm.fx.provider.failures");
        this.retryDelay = retryDelay;
        this.sleeper = sleeper;
        this.telemetry = telemetry;
    }

    private static RestClient configuredClient(RestClient.Builder builder, String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        return builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public CurrencyService.Observation synchronize(String base, String quote, String actor) {
        if (clock.instant().isBefore(circuitOpenUntil)) throw new FxProviderUnavailableException();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            var timer = telemetry.startFxAttempt();
            try {
                requests.increment();
                ProviderRate rate = client.get().uri("/api/v1/rates/{pair}", base + "-" + quote)
                        .accept(MediaType.APPLICATION_JSON).retrieve().body(ProviderRate.class);
                if (rate == null || rate.rate() == null || rate.observedAt() == null) {
                    throw new RestClientException("Invalid FX response");
                }
                telemetry.completeFxAttempt(timer, "success");
                String source = rate.source() == null || rate.source().isBlank() ? "HTTP_PROVIDER" : rate.source();
                currency.recordObservation(base, quote, rate.rate(), source, rate.observedAt(), actor);
                telemetry.fx("success");
                return new CurrencyService.Observation(base, quote, rate.rate(), source, rate.observedAt());
            } catch (RestClientException ex) {
                failures.increment();
                boolean retryable = retryable(ex);
                telemetry.completeFxAttempt(timer, retryable ? "transient_failure" : "permanent_failure");
                if (!retryable) {
                    telemetry.fx("unavailable");
                    throw new FxProviderUnavailableException();
                }
                if (attempt < MAX_ATTEMPTS) {
                    waitBeforeRetry(attempt);
                    continue;
                }
                circuitOpenUntil = clock.instant().plus(CIRCUIT_OPEN_FOR);
                telemetry.fx("unavailable");
                throw new FxProviderUnavailableException();
            }
        }
        throw new IllegalStateException("Unreachable retry state");
    }

    private static boolean retryable(RestClientException ex) {
        if (ex instanceof ResourceAccessException) return true;
        return ex instanceof RestClientResponseException response
                && (response.getStatusCode().value() == 429
                        || response.getStatusCode().is5xxServerError());
    }

    private void waitBeforeRetry(int failedAttempt) {
        try { sleeper.sleep(retryDelay.afterFailure(failedAttempt)); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new FxProviderUnavailableException(); }
    }

    record ProviderRate(BigDecimal rate, Instant observedAt, String source) {}
}
