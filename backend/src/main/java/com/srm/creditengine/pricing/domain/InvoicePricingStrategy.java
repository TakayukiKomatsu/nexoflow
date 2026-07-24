package com.srm.creditengine.pricing.domain;

import java.math.BigDecimal;
import java.util.List;

public final class InvoicePricingStrategy extends AbstractExactPricingStrategy {
    public String productType() { return "MERCANTILE_INVOICE"; }
    public String code() { return "INVOICE_V1"; }

    @Override
    public BigDecimal riskSpread(List<BigDecimal> effectiveSpreads) {
        return effectiveSpreads.stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No effective invoice risk spread"));
    }
}
