package com.srm.creditengine.pricing;

import com.srm.creditengine.currency.application.ReferenceRateService;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component public final class ChequePricingStrategy extends AbstractExactPricingStrategy {
    public String productType() { return "POST_DATED_CHEQUE"; }
    public String code() { return "CHEQUE_V1"; }

    @Override
    public ReferenceRateService.ProductSpread riskSpread(
            ReferenceRateService references, Instant at) {
        return references.productSpreads("POST_DATED_CHEQUE", at).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No effective cheque risk spread"));
    }
}
