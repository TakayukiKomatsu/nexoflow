package com.srm.creditengine.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "srm.clock.fixed-instant=2030-01-15T12:00:00Z")
@AutoConfigureMockMvc
class ResourceErrorApiContractTest {
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired Clock clock;
    @Autowired MeterRegistry meterRegistry;

    @ParameterizedTest
    @MethodSource("missingResourcePaths")
    void missingDomainResourceUsesSemanticNotFoundProblem(String path) throws Exception {
        mvc.perform(get(path).header("Authorization", "Bearer " + token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("The requested domain resource was not found."));
    }

    static Stream<String> missingResourcePaths() {
        UUID missing = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        return Stream.of(
                "/api/v1/assignors/" + missing,
                "/api/v1/receivables/" + missing,
                "/api/v1/pricing-quotes/" + missing,
                "/api/v1/settlements/" + missing);
    }

    @ParameterizedTest
    @MethodSource("malformedTypedParameters")
    void malformedPathAndQueryParametersUseSanitizedValidationProblems(
            String path, String expectedField) throws Exception {
        mvc.perform(get(path).header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Request validation failed."))
                .andExpect(jsonPath("$.violations[0].field").value(expectedField))
                .andExpect(jsonPath("$.violations[0].message").value("has an invalid value"));
    }

    static Stream<Arguments> malformedTypedParameters() {
        return Stream.of(
                Arguments.of("/api/v1/settlement-statements?page=abc", "page"),
                Arguments.of("/api/v1/settlement-statements?assignorId=bad", "assignorId"),
                Arguments.of("/api/v1/settlements/not-a-uuid", "settlementId"),
                Arguments.of(
                        "/api/v1/conversions?base=BRL&quote=USD&amount=1&at=bad",
                        "at"));
    }

    @Test
    void missingReceivableOnQuoteCreationUsesSemanticNotFoundProblem() throws Exception {
        mvc.perform(post("/api/v1/pricing-quotes")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "receivableId": "ffffffff-ffff-ffff-ffff-ffffffffffff",
                                  "settlementCurrency": "BRL"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        org.assertj.core.api.Assertions.assertThat(meterRegistry
                        .find("srm_quote_outcomes_total")
                        .tags("product", "UNKNOWN", "currency", "BRL", "result", "REJECTED")
                        .counter())
                .isNotNull()
                .extracting(io.micrometer.core.instrument.Counter::count)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE)
                .isGreaterThan(0);
    }

    @Test
    void rejectedPreviewAndReportQueriesEmitBoundedOutcomeMetrics() throws Exception {
        mvc.perform(post("/api/v1/settlement-previews")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quoteIds":["ffffffff-ffff-ffff-ffff-ffffffffffff"]}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/settlement-statements")
                        .header("Authorization", "Bearer " + token())
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());

        org.assertj.core.api.Assertions.assertThat(meterRegistry
                        .find("srm_preview_outcomes_total")
                        .tags("currency", "UNKNOWN", "result", "REJECTED")
                        .counter())
                .isNotNull();
        org.assertj.core.api.Assertions.assertThat(meterRegistry
                        .find("srm_statement_queries_total")
                        .tag("result", "REJECTED")
                        .counter())
                .isNotNull();
    }

    @Test
    void duplicateDatabaseConstraintsUseSanitizedConflictProblems() throws Exception {
        UUID firstAssignor = UUID.randomUUID();
        UUID secondAssignor = UUID.randomUUID();
        String taxId = "CONFLICT" + firstAssignor.toString().substring(0, 8);
        createAssignor(firstAssignor, taxId).andExpect(status().isCreated());

        createAssignor(secondAssignor, taxId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
                .andExpect(jsonPath("$.detail").value("The request conflicts with existing data."));

    }

    @Test
    void oversizedWritableFieldsFailValidationBeforePersistence() throws Exception {
        mvc.perform(post("/api/v1/assignors")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "legalName": "%s",
                                  "taxId": "VALID-TAX-ID",
                                  "active": true
                                }
                                """.formatted("x".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/assignors")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "legalName": "Valid Name",
                                  "taxId": "%s",
                                  "active": true
                                }
                                """.formatted("x".repeat(33))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        UUID assignorId = UUID.randomUUID();
        createAssignor(assignorId, "BOUND" + assignorId.toString().substring(0, 8))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/receivables")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receivableBody(assignorId, "x".repeat(51), "BRL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void receivableReferenceValuesUseDomainErrorsInsteadOfDatabaseConflicts() throws Exception {
        UUID assignorId = UUID.randomUUID();
        createAssignor(assignorId, "DOMAIN" + assignorId.toString().substring(0, 8))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/receivables")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receivableBody(assignorId, "MERCANTILE_INVOICE", "EUR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
        mvc.perform(post("/api/v1/receivables")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receivableBody(assignorId, "UNKNOWN_PRODUCT", "BRL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @ParameterizedTest
    @MethodSource("unsupportedStatementFilters")
    void statementClosedDomainFiltersRejectUnsupportedValues(String name, String value, String code)
            throws Exception {
        mvc.perform(get("/api/v1/settlement-statements")
                        .header("Authorization", "Bearer " + token())
                        .param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
    }

    static Stream<Arguments> unsupportedStatementFilters() {
        return Stream.of(
                Arguments.of("assetCurrency", "EUR", "UNSUPPORTED_CURRENCY"),
                Arguments.of("settlementCurrency", "EUR", "UNSUPPORTED_CURRENCY"),
                Arguments.of("productType", "BOGUS", "INVALID_REQUEST"));
    }

    private static String receivableBody(UUID assignorId, String productType, String currency) {
        return """
                {
                  "assignorId": "%s",
                  "productType": "%s",
                  "faceAmount": "1000.0000",
                  "faceCurrency": "%s",
                  "issueDate": "2030-01-15",
                  "dueDate": "2030-02-15"
                }
                """.formatted(assignorId, productType, currency);
    }

    private org.springframework.test.web.servlet.ResultActions createAssignor(UUID id, String taxId)
            throws Exception {
        return mvc.perform(post("/api/v1/assignors")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "id": "%s",
                          "legalName": "Conflict Contract Co",
                          "taxId": "%s",
                          "active": true
                        }
                        """.formatted(id, taxId)));
    }

    private String token() {
        var issuedAt = clock.instant();
        var claims = JwtClaimsSet.builder()
                .issuer("srm-credit-engine")
                .subject("00000000-0000-0000-0000-000000000302")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .claim("email", "error-contract-admin@srm.local")
                .claim("roles", List.of("ADMIN"))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
