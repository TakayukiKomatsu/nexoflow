package com.srm.creditengine.currency.application;

import com.srm.creditengine.currency.domain.ReferenceRatePolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferenceRateApplicationService implements ReferenceRateService {
    private final ReferenceRateRepository rates;
    private final ReferenceRateAuditRecorder audit;
    private final Clock clock;

    public ReferenceRateApplicationService(
            ReferenceRateRepository rates,
            ReferenceRateAuditRecorder audit,
            Clock clock) {
        this.rates = rates;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void recordBaseRate(
            String currency, BigDecimal monthlyRate, Instant effectiveAt, String actor) {
        ReferenceRatePolicy.validate(monthlyRate, effectiveAt, actor);
        UUID id = UUID.randomUUID();
        rates.recordBaseRate(id, currency, monthlyRate, effectiveAt, actor);
        audit.record(actor, "BASE_RATE_RECORDED", "BASE_RATE_VERSION", id, clock.instant());
    }

    @Override
    public List<BaseRate> baseRates(String currency, Instant effectiveAt) {
        return rates.baseRates(currency, effectiveAt);
    }

    @Override
    @Transactional
    public void recordProductSpread(
            String productType, BigDecimal monthlySpread, Instant effectiveAt, String actor) {
        ReferenceRatePolicy.validate(monthlySpread, effectiveAt, actor);
        UUID id = UUID.randomUUID();
        rates.recordProductSpread(id, productType, monthlySpread, effectiveAt, actor);
        audit.record(actor, "PRODUCT_SPREAD_RECORDED", "PRODUCT_SPREAD_VERSION", id, clock.instant());
    }

    @Override
    public List<ProductSpread> productSpreads(String productType, Instant effectiveAt) {
        return rates.productSpreads(productType, effectiveAt);
    }
}
