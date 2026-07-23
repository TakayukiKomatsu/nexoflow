package com.srm.creditengine.pricing.application;

import com.srm.creditengine.pricing.domain.PricingQuoteSnapshot;

/** Persists the append-only audit event associated with a pricing mutation. */
public interface PricingAuditRecorder {
    void recordQuoteCreated(String actor, PricingQuoteSnapshot snapshot);
}
