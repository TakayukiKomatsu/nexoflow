package com.srm.creditengine.receivable.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.srm.creditengine.identity.application.ActorContext;
import com.srm.creditengine.identity.application.ActorRole;
import com.srm.creditengine.identity.application.CurrentActor;
import com.srm.creditengine.receivable.application.ReceivableService;
import com.srm.creditengine.shared.runtime.SafeOperationalLogger;
import java.math.BigDecimal;
import java.time.LocalDate;
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

@WebMvcTest(ReceivableController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReceivableControllerTest {
    private static final UUID RECEIVABLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID ASSIGNOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000502");
    private static final LocalDate ISSUE_DATE = LocalDate.parse("2030-01-01");
    private static final LocalDate DUE_DATE = LocalDate.parse("2030-02-14");
    private static final ReceivableService.Receivable RECEIVABLE = new ReceivableService.Receivable(
            RECEIVABLE_ID,
            ASSIGNOR_ID,
            "MERCANTILE_INVOICE",
            new BigDecimal("1000.25"),
            "BRL",
            ISSUE_DATE,
            DUE_DATE,
            "REGISTERED",
            0L);

    @Autowired MockMvc mvc;
    @MockBean ReceivableService service;
    @MockBean ActorContext actors;
    @MockBean SafeOperationalLogger safeOperationalLogger;

    @Test
    void RECEIVABLE_001_acceptsDecimalStringAndReturnsCanonicalStringOnCreate() throws Exception {
        when(actors.currentActor()).thenReturn(new CurrentActor(
                UUID.fromString("00000000-0000-0000-0000-000000000503"),
                "operator@srm.local",
                Set.of(ActorRole.OPERATOR)));
        when(service.register(new ReceivableService.RegisterCommand(
                RECEIVABLE_ID,
                ASSIGNOR_ID,
                "MERCANTILE_INVOICE",
                new BigDecimal("1000.25"),
                "BRL",
                ISSUE_DATE,
                DUE_DATE,
                "operator@srm.local"))).thenReturn(RECEIVABLE);

        mvc.perform(post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("\"1000.25\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.faceAmount").value("1000.2500"));

        verify(service).register(new ReceivableService.RegisterCommand(
                RECEIVABLE_ID,
                ASSIGNOR_ID,
                "MERCANTILE_INVOICE",
                new BigDecimal("1000.25"),
                "BRL",
                ISSUE_DATE,
                DUE_DATE,
                "operator@srm.local"));
    }

    @Test
    void RECEIVABLE_002_rejectsNumericFaceAmount() throws Exception {
        mvc.perform(post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("1000.25")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @Test
    void RECEIVABLE_003_rejectsZeroFaceAmountString() throws Exception {
        mvc.perform(post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("\"0\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @Test
    void RECEIVABLE_004_rejectsFaceAmountStringWithMoreThanFourFractionDigits() throws Exception {
        mvc.perform(post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("\"1.00001\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @Test
    void RECEIVABLE_005_returnsCanonicalDecimalStringOnGet() throws Exception {
        when(service.get(RECEIVABLE_ID)).thenReturn(RECEIVABLE);

        mvc.perform(get("/api/v1/receivables/{id}", RECEIVABLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faceAmount").value("1000.2500"));
    }

    @Test
    void RECEIVABLE_006_returnsCanonicalDecimalStringsOnList() throws Exception {
        when(service.list()).thenReturn(List.of(RECEIVABLE));

        mvc.perform(get("/api/v1/receivables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].faceAmount").value("1000.2500"));
    }

    private static String validRequest(String faceAmountJson) {
        return "{\"id\":\"" + RECEIVABLE_ID
                + "\",\"assignorId\":\"" + ASSIGNOR_ID
                + "\",\"productType\":\"MERCANTILE_INVOICE\",\"faceAmount\":" + faceAmountJson
                + ",\"faceCurrency\":\"BRL\",\"issueDate\":\"2030-01-01\",\"dueDate\":\"2030-02-14\"}";
    }
}
