package com.srm.creditengine.pricing.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface PricingService {
    Breakdown simulate(Input input);
    Quote createQuote(UUID receivableId, String settlementCurrency, String actor);
    Quote getQuote(UUID quoteId);
    record Input(BigDecimal faceAmount, String faceCurrency, String productType, LocalDate dueDate, String settlementCurrency) {}
    record Breakdown(BigDecimal faceAmount, String faceCurrency, String settlementCurrency, BigDecimal baseRate, BigDecimal spread, String strategyCode, String dayCountConvention, BigDecimal termInMonths, BigDecimal discountedAmount, String fxBaseCurrency, String fxQuoteCurrency, BigDecimal fxRate, String fxSource, Instant fxObservedAt, BigDecimal settlementAmount, Instant pricedAt) {}
    record Quote(UUID id, UUID receivableId, String productType, LocalDate dueDate, Breakdown breakdown, Instant expiresAt, String status, String createdBy) {}
}
