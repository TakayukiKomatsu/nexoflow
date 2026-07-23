package com.srm.creditengine.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.srm.creditengine.currency.domain.FxRateMissingException;
import com.srm.creditengine.currency.domain.FxRateStaleException;
import com.srm.creditengine.currency.domain.UnsupportedCurrencyException;
import com.srm.creditengine.settlement.domain.AlreadyReversedException;
import com.srm.creditengine.settlement.domain.AlreadySettledException;
import com.srm.creditengine.settlement.application.IdempotencyKeyReusedException;
import com.srm.creditengine.settlement.application.ReversalIdempotencyKeyReusedException;
import com.srm.creditengine.settlement.application.SettlementPricingQuoteExpiredException;
import com.srm.creditengine.shared.api.DecimalString;
import io.micrometer.core.instrument.MeterRegistry;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import com.srm.creditengine.shared.runtime.SafeOperationalLogger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiErrorContractTest.CurrencyFailureController.class)
class ApiErrorContractTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MeterRegistry meterRegistry;


    @Test
    void missingRequiredInputUsesProblemDetailsAndCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/runtime/validation").header("X-Correlation-Id", "test-correlation-001"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.correlationId").value("test-correlation-001"))
                .andExpect(jsonPath("$.violations[0].field").value("value"));
    }

    @Test
    void unexpectedFailureIsSanitized() throws Exception {
        mockMvc.perform(get("/api/v1/runtime/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    @Test
    void unexpectedFailureEmitsSafeCorrelatedServerLogWithoutExceptionMessage() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(SafeOperationalLogger.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(get("/api/v1/runtime/failure")
                            .header("X-Correlation-Id", "safe-server-error-001")
                            .header("Idempotency-Key", "must-not-appear"))
                    .andExpect(status().isInternalServerError());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        ILoggingEvent failure = appender.list.stream()
                .filter(event -> event.getFormattedMessage().equals("UNEXPECTED_API_FAILURE"))
                .findFirst()
                .orElseThrow();
        java.util.Map<String, String> fields = failure.getKeyValuePairs().stream()
                .collect(java.util.stream.Collectors.toMap(
                        pair -> pair.key, pair -> String.valueOf(pair.value)));
        String rendered = failure.getFormattedMessage() + failure.getKeyValuePairs();
        org.assertj.core.api.Assertions.assertThat(fields)
                .containsEntry("event", "UNEXPECTED_API_FAILURE")
                .containsEntry("error_type", "IllegalStateException")
                .containsEntry("correlation_id", "safe-server-error-001");
        org.assertj.core.api.Assertions.assertThat(rendered)
                .doesNotContain("database-password")
                .doesNotContain("must-not-appear");
    }

    @Test
    void unknownApiPathUsesNotFoundProblem() throws Exception {
        mockMvc.perform(get("/api/v1/runtime/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unsupportedMediaTypeUsesSemanticProblem() throws Exception {
        mockMvc.perform(post("/api/v1/runtime/echo")
                        .contentType("text/plain")
                        .content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unsupportedCurrencyUsesRfc9457BadRequestContract() throws Exception {
        mockMvc.perform(get("/api/v1/runtime/currency-errors/unsupported"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"))
                .andExpect(jsonPath("$.type").value("urn:srm:error:unsupported-currency"))
                .andExpect(jsonPath("$.detail").value("The requested currency is not supported."));
    }

    @Test
    void missingFxRateUsesRfc9457UnprocessableEntityContract() throws Exception {
        mockMvc.perform(get("/api/v1/runtime/currency-errors/missing"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("FX_RATE_MISSING"))
                .andExpect(jsonPath("$.type").value("urn:srm:error:fx-rate-missing"))
                .andExpect(jsonPath("$.detail").value(
                        "No exchange rate is available for the requested currency pair."));
    }

    @Test
    void staleFxRateUsesRfc9457UnprocessableEntityContract() throws Exception {
        mockMvc.perform(get("/api/v1/runtime/currency-errors/stale"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("FX_RATE_STALE"))
                .andExpect(jsonPath("$.type").value("urn:srm:error:fx-rate-stale"))
                .andExpect(jsonPath("$.detail").value(
                        "No fresh exchange rate is available for the requested currency pair."));
    }

    @Test
    void reusedIdempotencyKeyRemainsADistinctConflict() throws Exception {
        assertConflict("/api/v1/runtime/currency-errors/idempotency-reused", "IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void reversalIdempotencyReuseRecordsOnlyAReversalConflict() throws Exception {
        double reversalBefore = counter(
                "srm_reversal_outcomes_total", "result", "CONFLICT");
        double settlementBefore = counter(
                "srm_settlement_outcomes_total",
                "currency", "UNKNOWN",
                "result", "CONFLICT");

        assertConflict(
                "/api/v1/runtime/currency-errors/reversal-idempotency-reused",
                "IDEMPOTENCY_KEY_REUSED");

        org.assertj.core.api.Assertions.assertThat(counter(
                        "srm_reversal_outcomes_total", "result", "CONFLICT"))
                .isEqualTo(reversalBefore + 1);
        org.assertj.core.api.Assertions.assertThat(counter(
                        "srm_settlement_outcomes_total",
                        "currency", "UNKNOWN",
                        "result", "CONFLICT"))
                .isEqualTo(settlementBefore);
    }

    @Test
    void settlementQuoteExpiryRecordsASettlementConflict() throws Exception {
        double before = counter(
                "srm_settlement_outcomes_total",
                "currency", "UNKNOWN",
                "result", "CONFLICT");

        assertConflict(
                "/api/v1/runtime/currency-errors/settlement-quote-expired",
                "PRICING_QUOTE_EXPIRED");

        org.assertj.core.api.Assertions.assertThat(counter(
                        "srm_settlement_outcomes_total",
                        "currency", "UNKNOWN",
                        "result", "CONFLICT"))
                .isEqualTo(before + 1);
    }

    private double counter(String name, String... tags) {
        var counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void alreadySettledRemainsADistinctConflict() throws Exception {
        assertConflict("/api/v1/runtime/currency-errors/already-settled", "ALREADY_SETTLED");
    }

    @Test
    void alreadySettledRecordsConflictWithSettlementCurrency() throws Exception {
        mockMvc.perform(get("/api/v1/runtime/currency-errors/already-settled-brl"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SETTLED"));

        org.assertj.core.api.Assertions.assertThat(
                        meterRegistry.find("srm_settlement_outcomes_total")
                                .tags("currency", "BRL", "result", "CONFLICT")
                                .counter())
                .isNotNull();
    }

    @Test
    void alreadyReversedRemainsADistinctConflict() throws Exception {
        assertConflict("/api/v1/runtime/currency-errors/already-reversed", "ALREADY_REVERSED");
    }

    private void assertConflict(String path, String code) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.type").value(
                        "urn:srm:error:" + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
    }

    @Test
    void nonIdempotencyMissingHeaderUsesGenericValidationProblem() throws Exception {
        mockMvc.perform(get("/api/v1/runtime/currency-errors/required-header"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.type").value("urn:srm:error:validation-failed"))
                .andExpect(jsonPath("$.detail").value("Request validation failed."))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("Idempotency-Key"));
    }

    @Test
    void missingIdempotencyHeaderUsesTheDedicatedProblemContract() throws Exception {
        mockMvc.perform(post("/api/v1/runtime/currency-errors/idempotency-header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"))
                .andExpect(jsonPath("$.detail").value("Idempotency-Key header is required."));
    }

    @Test
    void unreadableJsonUsesSafeRequestLevelValidationProblem() throws Exception {
        mockMvc.perform(post("/api/v1/runtime/currency-errors/decimal")
                        .contentType("application/json")
                        .content("{\"amount\":987654321.12}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Request validation failed."))
                .andExpect(jsonPath("$.violations.length()").value(1))
                .andExpect(jsonPath("$.violations[0].field").value("request"))
                .andExpect(jsonPath("$.violations[0].message")
                        .value("is malformed or contains an invalid value"))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("987654321.12"));
    }

    @RestController
    @RequestMapping("/api/v1/runtime/currency-errors")
    static class CurrencyFailureController {
        @GetMapping("/unsupported")
        void unsupported() {
            throw new UnsupportedCurrencyException();
        }

        @GetMapping("/missing")
        void missing() {
            throw new FxRateMissingException();
        }

        @GetMapping("/stale")
        void stale() {
            throw new FxRateStaleException();
        }

        @GetMapping("/idempotency-reused")
        void idempotencyReused() {
            throw new IdempotencyKeyReusedException();
        }

        @GetMapping("/reversal-idempotency-reused")
        void reversalIdempotencyReused() {
            throw new ReversalIdempotencyKeyReusedException();
        }

        @GetMapping("/settlement-quote-expired")
        void settlementQuoteExpired() {
            throw new SettlementPricingQuoteExpiredException();
        }

        @GetMapping("/already-settled")
        void alreadySettled() {
            throw new AlreadySettledException();
        }

        @GetMapping("/already-settled-brl")
        void alreadySettledBrl() {
            throw new AlreadySettledException("BRL");
        }

        @GetMapping("/already-reversed")
        void alreadyReversed() {
            throw new AlreadyReversedException();
        }

        @GetMapping("/required-header")
        void requiredHeader(@RequestHeader("X-Required") String required) {
        }

        @PostMapping("/idempotency-header")
        void idempotencyHeader(@RequestHeader("Idempotency-Key") String key) {
        }

        @PostMapping("/decimal")
        void decimal(@RequestBody DecimalRequest request) {
        }

        record DecimalRequest(DecimalString amount) {
        }
    }
}
