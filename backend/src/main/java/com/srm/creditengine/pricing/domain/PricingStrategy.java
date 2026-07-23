package com.srm.creditengine.pricing.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product-specific pricing seam; the engine never switches on product type.
 * Each strategy owns the computation that turns a face amount into its discounted
 * present value for its product, so behavior can diverge per product without
 * touching the orchestrating service.
 */
public interface PricingStrategy {
    String productType();
    String code();
    /**
     * Resolves the effective risk spread for this product at the pricing instant.
     */
    BigDecimal riskSpread(List<BigDecimal> effectiveSpreads);


    /**
     * Applies this product's discounting formula to {@code faceAmount} given the
     * effective base rate, spread, and elapsed term in months (per the
     * ACTUAL_DAYS_30_MONTH convention). Returns the discounted amount at full
     * working precision; the caller applies final currency rounding.
     */
    BigDecimal discount(BigDecimal faceAmount, BigDecimal baseRate, BigDecimal spread, BigDecimal termInMonths);
}
