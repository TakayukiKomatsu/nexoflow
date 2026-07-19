package com.srm.creditengine.currency.application;

public final class FxRateMissingException extends RuntimeException {
    public FxRateMissingException() {
        super("No exchange rate is available for the requested currency pair.");
    }
}
