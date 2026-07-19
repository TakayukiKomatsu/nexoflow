package com.srm.creditengine.currency.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface CurrencyService {
    void recordObservation(String base, String quote, BigDecimal rate, String source, Instant observedAt, String actor);
    List<Observation> observations(String base, String quote);
    Conversion resolveConversion(String base, String quote, BigDecimal amount, Instant at);
    record Observation(String base, String quote, BigDecimal rate, String source, Instant observedAt) {}
    record Conversion(Observation observation, BigDecimal unroundedConvertedAmount, BigDecimal settlementAmount) {}
}
