package com.srm.creditengine.settlement.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.srm.creditengine.identity.application.ActorContext;
import com.srm.creditengine.identity.application.ActorRole;
import com.srm.creditengine.identity.application.CurrentActor;
import com.srm.creditengine.settlement.application.SettlementService;
import java.math.BigDecimal;
import com.srm.creditengine.shared.runtime.SafeOperationalLogger;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SettlementController.class)
@AutoConfigureMockMvc(addFilters = false)
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
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quoteId").value(quote.toString())).andExpect(jsonPath("$.totalAmount").value("1900.0000"));
        verify(settlements).preview(List.of(quote), "operator@srm.local");
    }

    @Test
    void SETTLE_004_replayReturnsTheImmutableSettlementBody() throws Exception {
        UUID quote = UUID.fromString("00000000-0000-0000-0000-000000000502");
        UUID receivable = UUID.fromString("00000000-0000-0000-0000-000000000602");
        UUID settlement = UUID.fromString("00000000-0000-0000-0000-000000000702");
        when(actors.currentActor()).thenReturn(actor());
        when(settlements.settle(any(), eq("replay-004"), eq("operator@srm.local"))).thenReturn(new SettlementService.Result(settlement, "COMPLETED", List.of(new SettlementService.Item(quote, receivable, new BigDecimal("1900.0000"))), "BRL", new BigDecimal("1900.0000"), Instant.parse("2030-01-15T12:00:00Z"), true));
        mvc.perform(post("/api/v1/settlements").header("Idempotency-Key", "replay-004").contentType(MediaType.APPLICATION_JSON).content("{\"quoteIds\":[\"" + quote + "\"]}"))
                .andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replay", "true")).andExpect(jsonPath("$.settlementId").value(settlement.toString())).andExpect(jsonPath("$.totalAmount").value("1900.0000"));
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

    private CurrentActor actor() { return new CurrentActor(UUID.randomUUID(), "operator@srm.local", Set.of(ActorRole.OPERATOR)); }
}
