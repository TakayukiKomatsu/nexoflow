package com.srm.creditengine.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.creditengine.assignor.application.AssignorService;
import com.srm.creditengine.pricing.application.PricingService;
import com.srm.creditengine.receivable.application.ReceivableService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "srm.clock.fixed-instant=2030-01-15T12:00:00Z")
@AutoConfigureMockMvc
class JdbcSettlementServiceAuditTest {
    private static final String ACTOR = "admin-audit@srm.local";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("srm_credit_engine")
            .withUsername("srm")
            .withPassword("srm");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("srm.jwt-secret", () -> "srm-test-secret-do-not-use-32-bytes-minimum");
    }

    @Autowired SettlementService settlements;
    @Autowired PricingService pricing;
    @Autowired AssignorService assignors;
    @Autowired ReceivableService receivables;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtEncoder jwtEncoder;

    @Test
    void AUDIT_001_settleAndReverseEachWriteAnAppendOnlyAuditEvent() {
        UUID assignorId = UUID.randomUUID();
        assignors.create(new AssignorService.CreateCommand(assignorId, "Audit Co", "AUD" + assignorId.toString().substring(0, 8), true, "operator@srm.local"));
        UUID receivableId = UUID.randomUUID();
        receivables.register(new ReceivableService.RegisterCommand(receivableId, assignorId, "MERCANTILE_INVOICE", new BigDecimal("1000.00"), "BRL", LocalDate.parse("2030-01-01"), LocalDate.parse("2030-02-14"), "operator@srm.local"));
        var quote = pricing.createQuote(receivableId, "BRL", "operator@srm.local");

        var settled = settlements.settle(List.of(quote.id()), "audit-test-key", "operator@srm.local");

        Map<String, Object> settleEvent = jdbc.queryForMap(
                "select actor, action, target_type, target_id from audit_events where target_id=? and action='SETTLEMENT_CREATED'", settled.settlementId());
        assertThat(settleEvent.get("actor")).isEqualTo("operator@srm.local");
        assertThat(settleEvent.get("target_type")).isEqualTo("SETTLEMENT");

        var reversed = settlements.reverse(settled.settlementId(), "duplicate source document", "audit-test-reverse-key", "operator@srm.local");

        Map<String, Object> reverseEvent = jdbc.queryForMap(
                "select actor, action, target_type, target_id from audit_events where target_id=? and action='SETTLEMENT_REVERSED'", reversed.reversalId());
        assertThat(reverseEvent.get("actor")).isEqualTo("operator@srm.local");
        assertThat(reverseEvent.get("target_type")).isEqualTo("SETTLEMENT_REVERSAL");

        int count = jdbc.queryForObject("select count(*) from audit_events where action in ('SETTLEMENT_CREATED','SETTLEMENT_REVERSED')", Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(2);
    }

    @Test
    void settlementAndReversalHttpCorrelationIsStoredWithOnlyBoundedSafeMetadata()
            throws Exception {
        UUID assignorId = UUID.randomUUID();
        assignors.create(new AssignorService.CreateCommand(
                assignorId,
                "Correlated Audit Co",
                "COR" + assignorId.toString().substring(0, 8),
                true,
                ACTOR));
        UUID receivableId = UUID.randomUUID();
        receivables.register(new ReceivableService.RegisterCommand(
                receivableId,
                assignorId,
                "MERCANTILE_INVOICE",
                new BigDecimal("1000.00"),
                "BRL",
                LocalDate.parse("2030-01-01"),
                LocalDate.parse("2030-02-14"),
                ACTOR));
        UUID quoteId = pricing.createQuote(receivableId, "BRL", ACTOR).id();
        String token = adminToken();
        String settlementKey = "audit-correlation-settle-" + UUID.randomUUID();
        String settlementCorrelation = "settlement-audit-correlation-001";

        var settlementResponse = mockMvc.perform(post("/api/v1/settlements")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", settlementKey)
                        .header("X-Correlation-ID", settlementCorrelation)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("quoteIds", List.of(quoteId)))))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", settlementCorrelation))
                .andReturn()
                .getResponse();
        UUID settlementId = UUID.fromString(
                objectMapper.readTree(settlementResponse.getContentAsByteArray())
                        .path("settlementId")
                        .asText());

        Map<String, Object> settlementEvent = jdbc.queryForMap(
                "select correlation_id,safe_metadata::text as safe_metadata "
                        + "from audit_events where action='SETTLEMENT_CREATED' and target_id=?",
                settlementId);
        assertThat(settlementEvent.get("correlation_id")).isEqualTo(settlementCorrelation);
        String settlementMetadataText = (String) settlementEvent.get("safe_metadata");
        JsonNode settlementMetadata = objectMapper.readTree(settlementMetadataText);
        assertThat(settlementMetadata.isObject()).isTrue();
        assertThat(settlementMetadata.size()).isEqualTo(1);
        assertThat(settlementMetadata.path("itemCount").asInt()).isEqualTo(1);
        assertThat(settlementMetadataText)
                .hasSizeLessThanOrEqualTo(32)
                .doesNotContain(ACTOR, settlementKey, quoteId.toString(), "1000");

        String reversalKey = "audit-correlation-reverse-" + UUID.randomUUID();
        String reversalCorrelation = "reversal-audit-correlation-001";
        String reversalReason = "duplicate source document";
        var reversalResponse = mockMvc.perform(post(
                                "/api/v1/settlements/" + settlementId + "/reversals")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", reversalKey)
                        .header("X-Correlation-ID", reversalCorrelation)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("reason", reversalReason))))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", reversalCorrelation))
                .andReturn()
                .getResponse();
        UUID reversalId = UUID.fromString(
                objectMapper.readTree(reversalResponse.getContentAsByteArray())
                        .path("reversalId")
                        .asText());

        Map<String, Object> reversalEvent = jdbc.queryForMap(
                "select correlation_id,safe_metadata::text as safe_metadata "
                        + "from audit_events where action='SETTLEMENT_REVERSED' and target_id=?",
                reversalId);
        assertThat(reversalEvent.get("correlation_id")).isEqualTo(reversalCorrelation);
        String reversalMetadataText = (String) reversalEvent.get("safe_metadata");
        JsonNode reversalMetadata = objectMapper.readTree(reversalMetadataText);
        assertThat(reversalMetadata.isObject()).isTrue();
        assertThat(reversalMetadata.size()).isEqualTo(1);
        assertThat(reversalMetadata.path("settlementId").asText())
                .isEqualTo(settlementId.toString());
        assertThat(reversalMetadataText)
                .hasSizeLessThanOrEqualTo(64)
                .doesNotContain(ACTOR, reversalKey, reversalReason, quoteId.toString(), "1000");
    }

    private String adminToken() {
        Instant issuedAt = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("srm-credit-engine")
                .subject(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(600))
                .claim("email", ACTOR)
                .claim("roles", List.of("ADMIN"))
                .build();
        var tokenHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder
                .encode(JwtEncoderParameters.from(tokenHeader, claims))
                .getTokenValue();
    }
}
