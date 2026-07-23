package com.srm.creditengine.pricing.domain;

import java.math.BigDecimal;
import java.util.List;

public final class ChequePricingStrategy extends AbstractExactPricingStrategy {
    public String productType() { return "POST_DATED_CHEQUE"; }
    public String code() { return "CHEQUE_V1"; }

    @Override
    public BigDecimal riskSpread(List<BigDecimal> effectiveSpreads) {
        return effectiveSpreads.stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No effective cheque risk spread"));
    }
}
