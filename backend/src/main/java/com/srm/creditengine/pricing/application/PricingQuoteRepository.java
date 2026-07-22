package com.srm.creditengine.pricing.application;

import com.srm.creditengine.pricing.domain.PricingQuoteSnapshot;
import java.util.Optional;
import java.util.UUID;

public interface PricingQuoteRepository {
    void save(PricingQuoteSnapshot snapshot, String actor);

    Optional<PricingQuoteSnapshot> findById(UUID id);
}
