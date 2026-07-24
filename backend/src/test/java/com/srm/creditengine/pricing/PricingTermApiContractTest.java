package com.srm.creditengine.pricing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "srm.clock.fixed-instant=2030-01-15T12:00:00Z")
@AutoConfigureMockMvc(addFilters = false)
class PricingTermApiContractTest {
    @Autowired MockMvc mvc;

    @Test
    void simulationRejectsADueDateBeyondTheDocumentedTenYearTerm() throws Exception {
        mvc.perform(post("/api/v1/pricing-simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "faceAmount": "1000.0000",
                                  "faceCurrency": "BRL",
                                  "productType": "MERCANTILE_INVOICE",
                                  "dueDate": "2040-01-16",
                                  "settlementCurrency": "BRL"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Pricing term must not exceed ten years"));
    }
}
