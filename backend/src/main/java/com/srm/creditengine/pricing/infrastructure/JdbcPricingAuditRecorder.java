package com.srm.creditengine.pricing.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.creditengine.audit.application.AuditEventAppender;
import com.srm.creditengine.pricing.application.PricingAuditRecorder;
import com.srm.creditengine.pricing.domain.PricingQuoteSnapshot;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPricingAuditRecorder implements PricingAuditRecorder {
    private final AuditEventAppender auditEvents;
    private final ObjectMapper objectMapper;

    public JdbcPricingAuditRecorder(
            AuditEventAppender auditEvents, ObjectMapper objectMapper) {
        this.auditEvents = auditEvents;
        this.objectMapper = objectMapper;
    }

    @Override
    public void recordQuoteCreated(String actor, PricingQuoteSnapshot snapshot) {
        String metadata = objectMapper
                .valueToTree(Map.of(
                        "productType", snapshot.productType(),
                        "settlementCurrency", snapshot.settlementCurrency()))
                .toString();
        auditEvents.append(
                actor,
                "QUOTE_CREATED",
                "PRICING_QUOTE",
                snapshot.id(),
                snapshot.pricedAt(),
                metadata);
    }
}
