package com.srm.creditengine.currency.domain;

public final class UnsupportedCurrencyException extends RuntimeException {
    public UnsupportedCurrencyException() {
        super("The requested currency is not supported.");
    }
}
