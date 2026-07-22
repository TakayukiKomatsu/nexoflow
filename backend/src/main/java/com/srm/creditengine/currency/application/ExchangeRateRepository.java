package com.srm.creditengine.currency.application;

import com.srm.creditengine.currency.domain.FxObservation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExchangeRateRepository {
    void record(FxObservation observation, String actor, Instant createdAt);

    Optional<FxObservation> latest(String base, String quote, Instant at);

    List<FxObservation> observations(String base, String quote);
}
