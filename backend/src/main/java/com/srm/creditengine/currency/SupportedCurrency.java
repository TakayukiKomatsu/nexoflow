package com.srm.creditengine.currency;

import com.srm.creditengine.currency.application.UnsupportedCurrencyException;
import java.util.Locale;
import java.util.Set;

public final class SupportedCurrency {
    private static final Set<String> VALUES = Set.of("BRL", "USD");

    private SupportedCurrency() {
    }

    public static String require(String value) {
        if (value == null) {
            throw new UnsupportedCurrencyException();
        }
        String canonical = value.trim().toUpperCase(Locale.ROOT);
        if (!VALUES.contains(canonical)) {
            throw new UnsupportedCurrencyException();
        }
        return canonical;
    }
}
