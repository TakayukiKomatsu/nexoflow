package com.srm.creditengine.pricing.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.srm.creditengine.pricing.application.PricingService;
import com.srm.creditengine.identity.application.ActorContext;
import com.srm.creditengine.shared.runtime.SafeOperationalLogger;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PricingController.class) @AutoConfigureMockMvc(addFilters = false)
class PricingControllerTest {
    @Autowired MockMvc mvc; @MockBean PricingService pricing; @MockBean ActorContext actors; @MockBean SafeOperationalLogger safeOperationalLogger;
    @Test
    void PRICE_001_returnsExactDecimalStringsForServerAuthoritativeSimulation() throws Exception {
        when(pricing.simulate(any())).thenReturn(new PricingService.Breakdown(new BigDecimal("1000.0000"),"BRL","BRL",new BigDecimal("0.0100000000"),new BigDecimal("0.0150000000"),"INVOICE_V1","ACTUAL_DAYS_30_MONTH",new BigDecimal("1.0000000000"),new BigDecimal("975.6098"),"BRL","BRL",BigDecimal.ONE,"IDENTITY",Instant.parse("2030-01-15T12:00:00Z"),new BigDecimal("975.61"),Instant.parse("2030-01-15T12:00:00Z")));
        mvc.perform(post("/api/v1/pricing-simulations").contentType(MediaType.APPLICATION_JSON).content("{\"faceAmount\":\"1000.00\",\"faceCurrency\":\"BRL\",\"productType\":\"MERCANTILE_INVOICE\",\"dueDate\":\"2030-02-14\",\"settlementCurrency\":\"BRL\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.termInMonths").value("1.0000000000")).andExpect(jsonPath("$.settlementAmount").value("975.61"));
        verify(pricing).simulate(any());
    }
}
