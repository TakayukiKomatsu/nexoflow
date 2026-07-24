package com.srm.creditengine.pricing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import com.srm.creditengine.identity.application.ActorContext;
import com.srm.creditengine.pricing.application.PricingService;
import com.srm.creditengine.shared.api.DecimalString;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
class PricingController {
    private final PricingService pricing; private final ActorContext actors;
    PricingController(PricingService pricing, ActorContext actors) { this.pricing=pricing; this.actors=actors; }
    @PostMapping("/api/v1/pricing-simulations")
    PricingBreakdownResponse simulate(@Valid @RequestBody SimulationRequest request) { return PricingBreakdownResponse.from(pricing.simulate(new PricingService.Input(request.faceAmount().value(),request.faceCurrency(),request.productType(),request.dueDate(),request.settlementCurrency()))); }
    @PostMapping("/api/v1/pricing-quotes") @ResponseStatus(HttpStatus.CREATED)
    QuoteResponse quote(@Valid @RequestBody QuoteRequest request) { return QuoteResponse.from(pricing.createQuote(request.receivableId(),request.settlementCurrency(),actors.currentActor().email())); }
    @GetMapping("/api/v1/pricing-quotes/{id}")
    QuoteResponse getQuote(@PathVariable UUID id) { return QuoteResponse.from(pricing.getQuote(id)); }
    record SimulationRequest(
            @NotNull DecimalString faceAmount,
            @NotBlank @Pattern(regexp="[A-Z]{3}")
                    @Schema(allowableValues = {"BRL", "USD"}) String faceCurrency,
            @NotBlank @Schema(allowableValues = {"MERCANTILE_INVOICE", "POST_DATED_CHEQUE"}) String productType,
            @NotNull @Schema(description = "Due date no more than ten years after the server pricing date") LocalDate dueDate,
            @NotBlank @Pattern(regexp="[A-Z]{3}")
                    @Schema(allowableValues = {"BRL", "USD"}) String settlementCurrency) {
        @AssertTrue(message = "faceAmount must be positive with no more than 15 integer digits and 4 fractional digits")
        boolean isFaceAmountValid() {
            if (faceAmount == null) return true;
            BigDecimal value = faceAmount.value();
            return value.signum() > 0 && value.scale() <= 4 && value.precision() - value.scale() <= 15;
        }
    }
    record QuoteRequest(
            @NotNull UUID receivableId,
            @NotBlank @Schema(allowableValues = {"BRL", "USD"}) String settlementCurrency) {}
    @Schema(name = "PricingBreakdownResponse", requiredProperties = {
        "faceAmount", "faceCurrency", "settlementCurrency", "baseRate", "spread",
        "strategyCode", "dayCountConvention", "termInMonths", "discountedAmount",
        "fxBaseCurrency", "fxQuoteCurrency", "fxRate", "fxSource", "fxObservedAt",
        "settlementAmount", "pricedAt"
    })
    record PricingBreakdownResponse(
            String faceAmount,
            @Schema(allowableValues = {"BRL", "USD"}) String faceCurrency,
            @Schema(allowableValues = {"BRL", "USD"}) String settlementCurrency,
            String baseRate,
            String spread,
            String strategyCode,
            String dayCountConvention,
            String termInMonths,
            String discountedAmount,
            @Schema(allowableValues = {"BRL", "USD"}) String fxBaseCurrency,
            @Schema(allowableValues = {"BRL", "USD"}) String fxQuoteCurrency,
            String fxRate,
            String fxSource,
            Instant fxObservedAt,
            String settlementAmount,
            Instant pricedAt) {
        static PricingBreakdownResponse from(PricingService.Breakdown breakdown) {
            return new PricingBreakdownResponse(
                    decimal(breakdown.faceAmount()),
                    breakdown.faceCurrency(),
                    breakdown.settlementCurrency(),
                    decimal(breakdown.baseRate()),
                    decimal(breakdown.spread()),
                    breakdown.strategyCode(),
                    breakdown.dayCountConvention(),
                    decimal(breakdown.termInMonths()),
                    decimal(breakdown.discountedAmount()),
                    breakdown.fxBaseCurrency(),
                    breakdown.fxQuoteCurrency(),
                    decimal(breakdown.fxRate()),
                    breakdown.fxSource(),
                    breakdown.fxObservedAt(),
                    money(breakdown.settlementAmount()),
                    breakdown.pricedAt());
        }
    }
    @Schema(requiredProperties = {
        "id", "receivableId", "productType", "dueDate", "pricing", "expiresAt", "status", "createdBy"
    })
    record QuoteResponse(
            UUID id,
            UUID receivableId,
            @Schema(allowableValues = {"MERCANTILE_INVOICE", "POST_DATED_CHEQUE"}) String productType,
            LocalDate dueDate,
            PricingBreakdownResponse pricing,
            Instant expiresAt,
            @Schema(allowableValues = {"ACTIVE", "EXPIRED", "CONSUMED"}) String status,
            String createdBy) {
        static QuoteResponse from(PricingService.Quote quote) {
            return new QuoteResponse(
                    quote.id(),
                    quote.receivableId(),
                    quote.productType(),
                    quote.dueDate(),
                    PricingBreakdownResponse.from(quote.breakdown()),
                    quote.expiresAt(),
                    quote.status(),
                    quote.createdBy());
        }
    }
    private static String decimal(BigDecimal value) { return value.toPlainString(); }
    private static String money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_EVEN).toPlainString(); }
}
