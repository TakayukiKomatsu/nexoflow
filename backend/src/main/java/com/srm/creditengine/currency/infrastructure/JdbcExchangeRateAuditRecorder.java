package com.srm.creditengine.currency.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.creditengine.currency.application.ExchangeRateAuditRecorder;
import com.srm.creditengine.currency.domain.FxObservation;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExchangeRateAuditRecorder implements ExchangeRateAuditRecorder {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcExchangeRateAuditRecorder(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(String actor, UUID targetId, FxObservation observation, Instant occurredAt) {
        String metadata = objectMapper
                .valueToTree(Map.of("base", observation.base(), "quote", observation.quote()))
                .toString();
        jdbc.update(
                "insert into audit_events "
                        + "(id,actor,action,target_type,target_id,occurred_at,correlation_id,safe_metadata) "
                        + "values (?,?,?,?,?,?,?,?::jsonb)",
                UUID.randomUUID(),
                actor,
                "EXCHANGE_RATE_RECORDED",
                "EXCHANGE_RATE",
                targetId,
                Timestamp.from(occurredAt),
                MDC.get("correlationId"),
                metadata);
    }
}
