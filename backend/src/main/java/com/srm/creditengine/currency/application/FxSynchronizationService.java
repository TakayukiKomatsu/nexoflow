package com.srm.creditengine.currency.application;

public interface FxSynchronizationService {
    CurrencyService.Observation synchronize(String base, String quote, String actor);
}
