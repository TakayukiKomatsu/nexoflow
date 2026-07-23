package com.srm.creditengine.currency.domain;

public final class FxRateStaleException extends RuntimeException {
    public FxRateStaleException() {
        super("No fresh exchange rate is available for the requested currency pair.");
    }
}
