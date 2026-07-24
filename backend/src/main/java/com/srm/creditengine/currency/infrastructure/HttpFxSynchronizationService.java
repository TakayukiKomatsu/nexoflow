package com.srm.creditengine.currency.infrastructure;

import com.srm.creditengine.currency.application.CurrencyService;
import com.srm.creditengine.currency.application.FxProviderUnavailableException;
import com.srm.creditengine.currency.application.FxSynchronizationService;
import com.srm.creditengine.currency.domain.SupportedCurrency;
import com.srm.creditengine.shared.runtime.FinancialTelemetry;
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

/** HTTP-only provider adapter. Retry and circuit behavior is deliberately confined here. */
@Service
class HttpFxSynchronizationService implements FxSynchronizationService {
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration CIRCUIT_OPEN_FOR = Duration.ofSeconds(30);
    private final CurrencyService currency;
    private final RestClient client;
    private final Clock clock;
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
        String outcome = "REJECTED";
        try {
            String canonicalBase = SupportedCurrency.require(base);
            String canonicalQuote = SupportedCurrency.require(quote);
            if (canonicalBase.equals(canonicalQuote)) {
                throw new IllegalArgumentException("Base and quote currencies must differ");
            }
            outcome = "UNAVAILABLE";
            if (clock.instant().isBefore(circuitOpenUntil)) {
                throw new FxProviderUnavailableException();
            }
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                var timer = telemetry.startFxAttempt();
                try {
                    telemetry.fxProviderRequest();
                    ProviderRate rate = client.get()
                            .uri("/api/v1/rates/{pair}", canonicalBase + "-" + canonicalQuote)
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .body(ProviderRate.class);
                    String source = validatedSource(rate);
                    try {
                        currency.recordObservation(
                                canonicalBase,
                                canonicalQuote,
                                rate.rate(),
                                source,
                                rate.observedAt(),
                                actor);
                    } catch (IllegalArgumentException exception) {
                        telemetry.completeFxAttempt(timer, "permanent_failure");
                        throw new FxProviderUnavailableException();
                    }
                    telemetry.completeFxAttempt(timer, "success");
                    outcome = "SUCCESS";
                    return new CurrencyService.Observation(
                            canonicalBase, canonicalQuote, rate.rate(), source, rate.observedAt());
                } catch (RestClientException ex) {
                    telemetry.fxExternalFailure();
                    boolean retryable = retryable(ex);
                    telemetry.completeFxAttempt(
                            timer, retryable ? "transient_failure" : "permanent_failure");
                    if (!retryable) {
                        throw new FxProviderUnavailableException();
                    }
                    if (attempt < MAX_ATTEMPTS) {
                        waitBeforeRetry(attempt);
                        continue;
                    }
                    circuitOpenUntil = clock.instant().plus(CIRCUIT_OPEN_FOR);
                    throw new FxProviderUnavailableException();
                }
            }
            throw new IllegalStateException("Unreachable retry state");
        } finally {
            telemetry.fx(outcome);
        }
    }

    private static String validatedSource(ProviderRate rate) {
        if (rate == null || rate.rate() == null || rate.observedAt() == null) {
            throw new RestClientException("Invalid FX response");
        }
        BigDecimal value = rate.rate();
        int integerDigits = Math.max(0, value.precision() - value.scale());
        if (value.signum() <= 0 || value.scale() > 10 || integerDigits > 9) {
            throw new RestClientException("Invalid FX response");
        }
        String source = rate.source() == null || rate.source().isBlank()
                ? "HTTP_PROVIDER"
                : rate.source();
        if (source.length() > 50) {
            throw new RestClientException("Invalid FX response");
        }
        return source;
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
