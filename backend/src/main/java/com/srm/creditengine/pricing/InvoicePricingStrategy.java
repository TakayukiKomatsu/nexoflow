package com.srm.creditengine.pricing;

import com.srm.creditengine.currency.application.ReferenceRateService;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component public final class InvoicePricingStrategy extends AbstractExactPricingStrategy {
    public String productType() { return "MERCANTILE_INVOICE"; }
    public String code() { return "INVOICE_V1"; }

    @Override
    public ReferenceRateService.ProductSpread riskSpread(
            ReferenceRateService references, Instant at) {
        return references.productSpreads("MERCANTILE_INVOICE", at).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No effective invoice risk spread"));
    }
}
