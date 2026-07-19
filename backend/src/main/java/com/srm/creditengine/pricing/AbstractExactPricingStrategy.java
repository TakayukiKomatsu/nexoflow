package com.srm.creditengine.pricing;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Shared exact-decimal discounting math for product strategies that price using
 * the standard {@code face / (1 + base + spread)^(days/30)} formula. Product
 * strategies extend this to inherit the formula while remaining the seam invoked
 * per product; a strategy that needs different math overrides {@link #discount}
 * directly without touching this base or its siblings.
 */
abstract class AbstractExactPricingStrategy implements PricingStrategy {
    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);

    @Override
    public BigDecimal discount(BigDecimal faceAmount, BigDecimal baseRate, BigDecimal spread, BigDecimal termInMonths) {
        BigDecimal denominator = pow(BigDecimal.ONE.add(baseRate).add(spread), termInMonths);
        return faceAmount.divide(denominator, MC);
    }

    /** Decimal exponentiation uses exp(exponent * ln(base)); guard digits and HALF_EVEN prevent binary floating-point drift. */
    private static BigDecimal pow(BigDecimal base, BigDecimal exponent) {
        return exp(ln(base).multiply(exponent, MC));
    }

    private static BigDecimal ln(BigDecimal x) {
        BigDecimal y = x.subtract(BigDecimal.ONE, MC).divide(x.add(BigDecimal.ONE, MC), MC);
        BigDecimal y2 = y.multiply(y, MC);
        BigDecimal term = y;
        BigDecimal sum = term;
        for (int n = 3; n < 301; n += 2) {
            term = term.multiply(y2, MC);
            BigDecimal add = term.divide(BigDecimal.valueOf(n), MC);
            sum = sum.add(add, MC);
            if (add.abs().compareTo(BigDecimal.ONE.scaleByPowerOfTen(-35)) < 0) break;
        }
        return sum.multiply(BigDecimal.valueOf(2), MC);
    }

    private static BigDecimal exp(BigDecimal x) {
        BigDecimal sum = BigDecimal.ONE;
        BigDecimal term = BigDecimal.ONE;
        for (int n = 1; n < 301; n++) {
            term = term.multiply(x, MC).divide(BigDecimal.valueOf(n), MC);
            sum = sum.add(term, MC);
            if (term.abs().compareTo(BigDecimal.ONE.scaleByPowerOfTen(-35)) < 0) break;
        }
        return sum;
    }
}
