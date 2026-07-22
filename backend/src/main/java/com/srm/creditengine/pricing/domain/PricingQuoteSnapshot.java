package com.srm.creditengine.pricing.domain;

import com.srm.creditengine.pricing.application.PricingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public PricingService.Quote toQuote(Instant now) {
        BigDecimal effectiveFxRate = "IDENTITY".equals(fxSource) ? BigDecimal.ONE : fxRate;
        var breakdown = new PricingService.Breakdown(
                faceAmount.setScale(4, RoundingMode.HALF_EVEN),
                faceCurrency,
                settlementCurrency,
                baseRate,
                spread,
                strategyCode,
                dayCountConvention,
                termInMonths,
                discountedAmount.setScale(4, RoundingMode.HALF_EVEN),
                fxBaseCurrency,
                fxQuoteCurrency,
                effectiveFxRate,
                fxSource,
                fxObservedAt,
                settlementAmount.setScale(2, RoundingMode.HALF_EVEN),
                pricedAt);
        String effectiveStatus = "ACTIVE".equals(status) && !now.isBefore(expiresAt) ? "EXPIRED" : status;
        return new PricingService.Quote(id, receivableId, productType, dueDate, breakdown, expiresAt, effectiveStatus, createdBy);
    }
}
