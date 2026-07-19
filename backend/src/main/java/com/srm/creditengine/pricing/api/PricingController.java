package com.srm.creditengine.pricing.api;

import com.srm.creditengine.identity.application.ActorContext;
import com.srm.creditengine.pricing.application.PricingService;
import com.srm.creditengine.shared.api.DecimalString;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
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
    Response simulate(@Valid @RequestBody SimulationRequest request) { return Response.from(pricing.simulate(new PricingService.Input(request.faceAmount().value(),request.faceCurrency(),request.productType(),request.dueDate(),request.settlementCurrency()))); }
    @PostMapping("/api/v1/pricing-quotes") @ResponseStatus(HttpStatus.CREATED)
    QuoteResponse quote(@Valid @RequestBody QuoteRequest request) { return QuoteResponse.from(pricing.createQuote(request.receivableId(),request.settlementCurrency(),actors.currentActor().email())); }
    @GetMapping("/api/v1/pricing-quotes/{id}")
    QuoteResponse getQuote(@PathVariable UUID id) { return QuoteResponse.from(pricing.getQuote(id)); }
    record SimulationRequest(@NotNull DecimalString faceAmount, @NotBlank @Pattern(regexp="[A-Z]{3}") String faceCurrency, @NotBlank String productType, @NotNull LocalDate dueDate, @NotBlank @Pattern(regexp="[A-Z]{3}") String settlementCurrency) {
        @AssertTrue(message = "faceAmount must be positive with no more than 15 integer digits and 4 fractional digits")
        boolean isFaceAmountValid() {
            if (faceAmount == null) return true;
            BigDecimal value = faceAmount.value();
            return value.signum() > 0 && value.scale() <= 4 && value.precision() - value.scale() <= 15;
        }
    }
    record QuoteRequest(@NotNull UUID receivableId, @NotBlank String settlementCurrency) {}
    record Response(String faceAmount, String faceCurrency, String settlementCurrency, String baseRate, String spread, String strategyCode, String dayCountConvention, String termInMonths, String discountedAmount, String fxBaseCurrency, String fxQuoteCurrency, String fxRate, String fxSource, Instant fxObservedAt, String settlementAmount, Instant pricedAt) {
        static Response from(PricingService.Breakdown b) { return new Response(decimal(b.faceAmount()),b.faceCurrency(),b.settlementCurrency(),decimal(b.baseRate()),decimal(b.spread()),b.strategyCode(),b.dayCountConvention(),decimal(b.termInMonths()),decimal(b.discountedAmount()),b.fxBaseCurrency(),b.fxQuoteCurrency(),decimal(b.fxRate()),b.fxSource(),b.fxObservedAt(),decimal(b.settlementAmount()),b.pricedAt()); }
    }
    record QuoteResponse(UUID id, UUID receivableId, String productType, LocalDate dueDate, Response pricing, Instant expiresAt, String status, String createdBy) {
        static QuoteResponse from(PricingService.Quote quote) {
            return new QuoteResponse(
                    quote.id(),
                    quote.receivableId(),
                    quote.productType(),
                    quote.dueDate(),
                    Response.from(quote.breakdown()),
                    quote.expiresAt(),
                    quote.status(),
                    quote.createdBy());
        }
    }
    private static String decimal(BigDecimal value) { return value.toPlainString(); }
}
