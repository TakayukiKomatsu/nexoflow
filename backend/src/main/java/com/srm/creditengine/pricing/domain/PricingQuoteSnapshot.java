package com.srm.creditengine.pricing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Complete immutable persistence snapshot of an authoritative pricing decision. */
public record PricingQuoteSnapshot(
        UUID id,
        UUID receivableId,
        String productType,
        LocalDate dueDate,
        String settlementCurrency,
        BigDecimal faceAmount,
        String faceCurrency,
        Instant pricedAt,
        Instant expiresAt,
        BigDecimal baseRate,
        BigDecimal spread,
        String strategyCode,
        String dayCountConvention,
        BigDecimal termInMonths,
        BigDecimal discountedAmount,
        String fxBaseCurrency,
        String fxQuoteCurrency,
        BigDecimal fxRate,
        String fxSource,
        Instant fxObservedAt,
        BigDecimal settlementAmount,
        String createdBy,
        String status) {
}
