package com.srm.creditengine.currency.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.creditengine.audit.application.AuditEventAppender;
import com.srm.creditengine.currency.application.ExchangeRateAuditRecorder;
import com.srm.creditengine.currency.domain.FxObservation;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExchangeRateAuditRecorder implements ExchangeRateAuditRecorder {
    private final AuditEventAppender auditEvents;
    private final ObjectMapper objectMapper;

    public JdbcExchangeRateAuditRecorder(
            AuditEventAppender auditEvents, ObjectMapper objectMapper) {
        this.auditEvents = auditEvents;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(String actor, UUID targetId, FxObservation observation, Instant occurredAt) {
        String metadata = objectMapper
                .valueToTree(Map.of("base", observation.base(), "quote", observation.quote()))
                .toString();
        auditEvents.append(
                actor,
                "EXCHANGE_RATE_RECORDED",
                "EXCHANGE_RATE",
                targetId,
                occurredAt,
                metadata);
    }
}
