package com.srm.creditengine.pricing.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ReceivableQuoteReader {
    Optional<LockedReceivable> lockRegistered(UUID id);

    record LockedReceivable(
            UUID id,
            String productType,
            BigDecimal faceAmount,
            String faceCurrency,
            LocalDate dueDate,
            String status) {
    }
}
