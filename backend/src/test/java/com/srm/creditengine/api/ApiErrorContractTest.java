package com.srm.creditengine.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiErrorContractTest {
    @Autowired
    private MockMvc mockMvc;

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
}
