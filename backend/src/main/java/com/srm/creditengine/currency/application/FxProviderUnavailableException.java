package com.srm.creditengine.currency.application;

public final class FxProviderUnavailableException extends RuntimeException {
    public FxProviderUnavailableException() { super("The FX provider is temporarily unavailable."); }
}
