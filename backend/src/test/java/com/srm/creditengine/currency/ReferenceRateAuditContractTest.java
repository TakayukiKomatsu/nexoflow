package com.srm.creditengine.currency;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
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
class ReferenceRateAuditContractTest {
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired Clock clock;

    @Test
    void adminReferenceRateMutationExposesCreatorAndAppendOnlyAuditEvent() throws Exception {
        String token = adminToken("reference-admin@srm.local");

        mvc.perform(post("/api/v1/base-rates")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Correlation-Id", "reference-rate-audit-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currency": "BRL",
                                  "monthlyRate": "0.0230000000",
                                  "effectiveAt": "2041-01-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/base-rates")
                        .header("Authorization", "Bearer " + token)
                        .param("currency", "BRL")
                        .param("effectiveAt", "2041-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].monthlyRate").isString())
                .andExpect(jsonPath("$[0].monthlyRate").value("0.0230000000"))
                .andExpect(jsonPath("$[0].createdBy").value("reference-admin@srm.local"));

        mvc.perform(get("/api/v1/audit-events")
                        .header("Authorization", "Bearer " + token)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action == 'BASE_RATE_RECORDED' && @.actor == 'reference-admin@srm.local')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$[?(@.action == 'BASE_RATE_RECORDED' && @.targetType == 'BASE_RATE_VERSION')]")
                        .isNotEmpty());
    }

    @Test
    void adminProductSpreadMutationExposesCreatorAndAppendOnlyAuditEvent() throws Exception {
        String token = adminToken("spread-admin@srm.local");

        mvc.perform(post("/api/v1/product-spreads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "MERCANTILE_INVOICE",
                                  "monthlySpread": "0.0270000000",
                                  "effectiveAt": "2042-01-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/product-spreads")
                        .header("Authorization", "Bearer " + token)
                        .param("productType", "MERCANTILE_INVOICE")
                        .param("effectiveAt", "2042-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].monthlySpread").isString())
                .andExpect(jsonPath("$[0].monthlySpread").value("0.0270000000"))
                .andExpect(jsonPath("$[0].createdBy").value("spread-admin@srm.local"));

        mvc.perform(get("/api/v1/audit-events")
                        .header("Authorization", "Bearer " + token)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action == 'PRODUCT_SPREAD_RECORDED' && @.actor == 'spread-admin@srm.local')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$[?(@.action == 'PRODUCT_SPREAD_RECORDED' && @.targetType == 'PRODUCT_SPREAD_VERSION')]")
                        .isNotEmpty());
    }

    @Test
    void financialJsonRejectsNumbersAndReturnsCanonicalDecimalStrings() throws Exception {
        String token = adminToken("decimal-contract-admin@srm.local");

        mvc.perform(post("/api/v1/exchange-rates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "base": "USD",
                                  "quote": "BRL",
                                  "rate": 5.3,
                                  "source": "numeric-contract",
                                  "observedAt": "2043-01-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(post("/api/v1/exchange-rates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "base": "USD",
                                  "quote": "BRL",
                                  "rate": "5.2000000000",
                                  "source": "decimal-contract",
                                  "observedAt": "2043-01-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/exchange-rates")
                        .header("Authorization", "Bearer " + token)
                        .param("base", "USD")
                        .param("quote", "BRL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rate").isString())
                .andExpect(jsonPath("$[0].rate").value("5.2000000000"));

        mvc.perform(get("/api/v1/conversions")
                        .header("Authorization", "Bearer " + token)
                        .param("base", "USD")
                        .param("quote", "BRL")
                        .param("amount", "100.0000")
                        .param("at", "2043-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observation.rate").isString())
                .andExpect(jsonPath("$.unroundedConvertedAmount").isString())
                .andExpect(jsonPath("$.unroundedConvertedAmount").value("520.0000000000"))
                .andExpect(jsonPath("$.settlementAmount").isString())
                .andExpect(jsonPath("$.settlementAmount").value("520.00"));
    }

    @Test
    void referenceRateCurrencyUsesTheSharedSupportedCurrencyContract() throws Exception {
        String token = adminToken("unsupported-currency-admin@srm.local");

        mvc.perform(post("/api/v1/base-rates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currency": "EUR",
                                  "monthlyRate": "0.0100000000",
                                  "effectiveAt": "2044-01-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));

        mvc.perform(get("/api/v1/base-rates")
                        .header("Authorization", "Bearer " + token)
                        .param("currency", "EUR")
                        .param("effectiveAt", "2044-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
    }

    @Test
    void ratePrecisionIsRejectedBeforePersistenceAndTheExactMaximumRoundTrips() throws Exception {
        String token = adminToken("precision-contract-admin@srm.local");

        mvc.perform(post("/api/v1/exchange-rates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "base": "BRL",
                                  "quote": "USD",
                                  "rate": "1.12345678901",
                                  "source": "precision-contract",
                                  "observedAt": "2045-01-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/exchange-rates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "base": "BRL",
                                  "quote": "USD",
                                  "rate": "1000000000.0000000000",
                                  "source": "precision-contract",
                                  "observedAt": "2045-01-01T00:00:01Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/base-rates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currency": "BRL",
                                  "monthlyRate": "0.12345678901",
                                  "effectiveAt": "2045-01-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(post("/api/v1/exchange-rates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "base": "BRL",
                                  "quote": "USD",
                                  "rate": "999999999.1234567890",
                                  "source": "precision-contract",
                                  "observedAt": "2045-01-01T00:00:02Z"
                                }
                                """))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/exchange-rates")
                        .header("Authorization", "Bearer " + token)
                        .param("base", "BRL")
                        .param("quote", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rate").value("999999999.1234567890"));
    }

    private String adminToken(String email) {
        var issuedAt = clock.instant();
        var claims = JwtClaimsSet.builder()
                .issuer("srm-credit-engine")
                .subject("00000000-0000-0000-0000-000000000302")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .claim("email", email)
                .claim("roles", List.of("ADMIN"))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
