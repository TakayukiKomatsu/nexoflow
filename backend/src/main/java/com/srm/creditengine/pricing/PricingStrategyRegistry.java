package com.srm.creditengine.pricing;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PricingStrategyRegistry {
    private final Map<String, PricingStrategy> strategies;
    public PricingStrategyRegistry(java.util.List<PricingStrategy> strategies) { this.strategies = strategies.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(PricingStrategy::productType, s -> s)); }
    public PricingStrategy forProduct(String productType) { var strategy = strategies.get(productType); if (strategy == null) throw new IllegalArgumentException("Unsupported product type: " + productType); return strategy; }
}
