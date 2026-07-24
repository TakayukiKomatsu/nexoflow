package com.srm.creditengine.settlement.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.srm.creditengine.identity.application.ActorContext;
import com.srm.creditengine.identity.application.ActorRole;
import com.srm.creditengine.identity.application.CurrentActor;
import com.srm.creditengine.settlement.application.SettlementService;
import com.srm.creditengine.settlement.domain.PricingQuoteExpiredException;
import java.math.BigDecimal;
import com.srm.creditengine.shared.runtime.SafeOperationalLogger;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import com.srm.creditengine.shared.api.JacksonConfiguration;

@WebMvcTest(SettlementController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(JacksonConfiguration.class)
class SettlementControllerTest {
    @Autowired MockMvc mvc;
    @MockBean SettlementService settlements;
    @MockBean ActorContext actors;
    @MockBean SafeOperationalLogger safeOperationalLogger;

    @Test
    void SETTLE_001_previewPreservesRequestOrderAndExactTotal() throws Exception {
        UUID quote = UUID.fromString("00000000-0000-0000-0000-000000000501");
        UUID receivable = UUID.fromString("00000000-0000-0000-0000-000000000601");
        when(actors.currentActor()).thenReturn(actor());
        when(settlements.preview(any(), eq("operator@srm.local"))).thenReturn(new SettlementService.Preview(
                List.of(new SettlementService.Item(quote, receivable, new BigDecimal("1900.0000"))), "BRL", new BigDecimal("1900.0000"), Instant.parse("2030-01-15T12:00:00Z"), Instant.parse("2030-01-15T12:15:00Z")));
        mvc.perform(post("/api/v1/settlement-previews").contentType(MediaType.APPLICATION_JSON).content("{\"quoteIds\":[\"" + quote + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quoteId").value(quote.toString()))
                .andExpect(jsonPath("$.items[0].settlementAmount").value("1900.00"))
                .andExpect(jsonPath("$.totalAmount").value("1900.00"));
        verify(settlements).preview(List.of(quote), "operator@srm.local");
    }

    @Test
    void previewRejectsMoreThanOneHundredQuoteIdsBeforeCallingTheApplication() throws Exception {
        mvc.perform(post("/api/v1/settlement-previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteIdsRequest(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(settlements);
    }

    @Test
    void previewAcceptsExactlyOneHundredQuoteIds() throws Exception {
        when(actors.currentActor()).thenReturn(actor());
        when(settlements.preview(any(), eq("operator@srm.local"))).thenReturn(
                new SettlementService.Preview(
                        List.of(),
                        "BRL",
                        BigDecimal.ZERO,
                        Instant.parse("2030-01-15T12:00:00Z"),
                        Instant.parse("2030-01-15T12:15:00Z")));

        mvc.perform(post("/api/v1/settlement-previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteIdsRequest(100)))
                .andExpect(status().isOk());

        verify(settlements).preview(
                argThat(quoteIds -> quoteIds.size() == 100), eq("operator@srm.local"));
    }

    @Test
    void SETTLE_004_replayReturnsTheImmutableSettlementBody() throws Exception {
        UUID quote = UUID.fromString("00000000-0000-0000-0000-000000000502");
        UUID receivable = UUID.fromString("00000000-0000-0000-0000-000000000602");
        UUID settlement = UUID.fromString("00000000-0000-0000-0000-000000000702");
        when(actors.currentActor()).thenReturn(actor());
        when(settlements.settle(any(), eq("replay-004"), eq("operator@srm.local"))).thenReturn(new SettlementService.Result(settlement, "COMPLETED", List.of(new SettlementService.Item(quote, receivable, new BigDecimal("1900.0000"))), "BRL", new BigDecimal("1900.0000"), Instant.parse("2030-01-15T12:00:00Z"), true));
        mvc.perform(post("/api/v1/settlements").header("Idempotency-Key", "replay-004").contentType(MediaType.APPLICATION_JSON).content("{\"quoteIds\":[\"" + quote + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.settlementId").value(settlement.toString()))
                .andExpect(jsonPath("$.items[0].settlementAmount").value("1900.00"))
                .andExpect(jsonPath("$.totalAmount").value("1900.00"));
    }

    @Test
    void REVERSE_007_replayReturnsOriginalReversalId() throws Exception {
        UUID settlement = UUID.fromString("00000000-0000-0000-0000-000000000707");
        UUID reversal = UUID.fromString("00000000-0000-0000-0000-000000000808");
        when(actors.currentActor()).thenReturn(actor());
        when(settlements.reverse(eq(settlement), eq("duplicate source document"), eq("reverse-007"), eq("operator@srm.local")))
                .thenReturn(new SettlementService.Reversal(reversal, settlement, "duplicate source document", Instant.parse("2030-01-16T09:00:00Z"), true));
        mvc.perform(post("/api/v1/settlements/" + settlement + "/reversals").header("Idempotency-Key", "reverse-007").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"duplicate source document\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replay", "true")).andExpect(jsonPath("$.reversalId").value(reversal.toString()));
    }

    @Test
    void settlementRequiresIdempotencyKey() throws Exception {
        mvc.perform(post("/api/v1/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"))
                .andExpect(jsonPath("$.type").value("urn:srm:error:idempotency-key-required"))
                .andExpect(jsonPath("$.detail").value("Idempotency-Key header is required."));
    }

    @Test
    void reversalRequiresIdempotencyKey() throws Exception {
        mvc.perform(post("/api/v1/settlements/" + settlementId() + "/reversals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reversalRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"))
                .andExpect(jsonPath("$.type").value("urn:srm:error:idempotency-key-required"))
                .andExpect(jsonPath("$.detail").value("Idempotency-Key header is required."));
    }

    @ParameterizedTest
    @MethodSource("invalidIdempotencyKeys")
    void settlementRejectsInvalidIdempotencyKey(String key) throws Exception {
        mvc.perform(post("/api/v1/settlements")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.type").value("urn:srm:error:validation-failed"))
                .andExpect(jsonPath("$.detail").value("Request validation failed."));
    }

    @ParameterizedTest
    @MethodSource("invalidIdempotencyKeys")
    void reversalRejectsInvalidIdempotencyKey(String key) throws Exception {
        mvc.perform(post("/api/v1/settlements/" + settlementId() + "/reversals")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reversalRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.type").value("urn:srm:error:validation-failed"))
                .andExpect(jsonPath("$.detail").value("Request validation failed."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"totalAmount", "actor", "status"})
    void settlementRejectsUnknownProperties(String property) throws Exception {
        mvc.perform(post("/api/v1/settlements")
                        .header("Idempotency-Key", "strict-settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteRequestWith(property)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("request"))
                .andExpect(jsonPath("$.type").value("urn:srm:error:validation-failed"))
                .andExpect(jsonPath("$.detail").value("Request validation failed."))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("server-owned"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"totalAmount", "actor", "status"})
    void reversalRejectsUnknownProperties(String property) throws Exception {
        mvc.perform(post("/api/v1/settlements/" + settlementId() + "/reversals")
                        .header("Idempotency-Key", "strict-reversal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reversalRequestWith(property)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("request"))
                .andExpect(jsonPath("$.type").value("urn:srm:error:validation-failed"))
                .andExpect(jsonPath("$.detail").value("Request validation failed."))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("server-owned"));
    }

    @Test
    void expiredQuoteUsesSafeConflictProblem() throws Exception {
        when(actors.currentActor()).thenReturn(actor());
        when(settlements.settle(any(), anyString(), eq("operator@srm.local")))
                .thenThrow(new PricingQuoteExpiredException());

        mvc.perform(post("/api/v1/settlements")
                        .header("Idempotency-Key", "expired-quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteRequest()))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("PRICING_QUOTE_EXPIRED"))
                .andExpect(jsonPath("$.type").value("urn:srm:error:pricing-quote-expired"))
                .andExpect(jsonPath("$.detail").value("A pricing quote expired. Create a fresh quote and preview."))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("00000000-0000-0000-0000-000000000501"));
    }

    private static java.util.stream.Stream<String> invalidIdempotencyKeys() {
        return java.util.stream.Stream.of(" ", "x".repeat(201));
    }

    private static String quoteRequest() {
        return "{\"quoteIds\":[\"00000000-0000-0000-0000-000000000501\"]}";
    }

    private static String quoteIdsRequest(int count) {
        String quoteIds = java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> "\"" + new UUID(0, index) + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"quoteIds\":[" + quoteIds + "]}";
    }

    private static String quoteRequestWith(String property) {
        return "{\"quoteIds\":[\"00000000-0000-0000-0000-000000000501\"],\""
                + property + "\":\"server-owned\"}";
    }

    private static String reversalRequest() {
        return "{\"reason\":\"duplicate source document\"}";
    }

    private static String reversalRequestWith(String property) {
        return "{\"reason\":\"duplicate source document\",\"" + property + "\":\"server-owned\"}";
    }

    private static UUID settlementId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000707");
    }

    private CurrentActor actor() { return new CurrentActor(UUID.randomUUID(), "operator@srm.local", Set.of(ActorRole.OPERATOR)); }
}
