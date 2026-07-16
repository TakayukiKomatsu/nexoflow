package com.srm.creditengine.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FxConversionService {
    public BigDecimal direct(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(10, RoundingMode.HALF_EVEN);
    }

    public BigDecimal inverse(BigDecimal amount, BigDecimal rate) {
        return amount.divide(rate, 10, RoundingMode.HALF_EVEN);
    }
}
