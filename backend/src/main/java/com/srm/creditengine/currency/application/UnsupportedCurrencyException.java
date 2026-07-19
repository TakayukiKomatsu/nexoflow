package com.srm.creditengine.currency.application;

public final class UnsupportedCurrencyException extends RuntimeException {
    public UnsupportedCurrencyException() {
        super("The requested currency is not supported.");
    }
}
