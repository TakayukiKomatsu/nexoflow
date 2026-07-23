package com.srm.creditengine.pricing.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.creditengine.pricing.application.PricingAuditRecorder;
import com.srm.creditengine.pricing.domain.PricingQuoteSnapshot;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPricingAuditRecorder implements PricingAuditRecorder {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcPricingAuditRecorder(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void recordQuoteCreated(String actor, PricingQuoteSnapshot snapshot) {
        String metadata = objectMapper
                .valueToTree(Map.of(
                        "productType", snapshot.productType(),
                        "settlementCurrency", snapshot.settlementCurrency()))
                .toString();
        jdbc.update(
                "insert into audit_events "
                        + "(id,actor,action,target_type,target_id,occurred_at,correlation_id,safe_metadata) "
                        + "values (?,?,?,?,?,?,?,?::jsonb)",
                UUID.randomUUID(),
                actor,
                "QUOTE_CREATED",
                "PRICING_QUOTE",
                snapshot.id(),
                Timestamp.from(snapshot.pricedAt()),
                MDC.get("correlationId"),
                metadata);
    }
}
