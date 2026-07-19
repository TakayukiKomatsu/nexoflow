package com.srm.creditengine.reporting.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.srm.creditengine.reporting.application.SettlementStatementService;
import com.srm.creditengine.shared.runtime.SafeOperationalLogger;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SettlementStatementController.class)
@AutoConfigureMockMvc(addFilters = false)
class SettlementStatementControllerTest {
    @Autowired MockMvc mvc;
    @MockBean SettlementStatementService statements;
    @MockBean SafeOperationalLogger safeOperationalLogger;

    @Test
    void REPORT_REV_003_exposesSignedCompensatingEntry() throws Exception {
        UUID entry = UUID.fromString("00000000-0000-0000-0000-000000000901");
        UUID settlement = UUID.fromString("00000000-0000-0000-0000-000000000902");
        UUID reversal = UUID.fromString("00000000-0000-0000-0000-000000000903");
        when(statements.query(any())).thenReturn(new SettlementStatementService.Page(List.of(
                new SettlementStatementService.Entry(entry, "REVERSAL", new BigDecimal("-975.6100"), Instant.parse("2030-01-16T09:00:00Z"), settlement, reversal, UUID.randomUUID(), "BRL", "BRL", "INVOICE", UUID.randomUUID())), 0, 50, false));
        mvc.perform(get("/api/v1/settlement-statements").param("from", "2030-01-15T00:00:00Z").param("to", "2030-01-17T00:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries[0].entryId").value(entry.toString())).andExpect(jsonPath("$.entries[0].entryType").value("REVERSAL")).andExpect(jsonPath("$.entries[0].signedAmount").value("-975.6100")).andExpect(jsonPath("$.entries[0].settlementId").value(settlement.toString()));
    }
}
