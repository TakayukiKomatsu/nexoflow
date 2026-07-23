package com.srm.creditengine.currency.api;

import com.srm.creditengine.currency.application.ReferenceRateService;
import com.srm.creditengine.identity.application.ActorContext;
import com.srm.creditengine.shared.api.DecimalString;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReferenceRateController {
    private final ReferenceRateService references;
    private final ActorContext actors;
    ReferenceRateController(ReferenceRateService references, ActorContext actors) {
        this.references = references;
        this.actors = actors;
    }

    @PostMapping("/api/v1/base-rates") @ResponseStatus(HttpStatus.CREATED)
    void createBase(@Valid @RequestBody BaseRateRequest request) {
        references.recordBaseRate(
                request.currency(), request.monthlyRate().value(), request.effectiveAt(), actors.currentActor().email());
    }
    @GetMapping("/api/v1/base-rates")
    List<BaseRateResponse> listBase(@RequestParam String currency, @RequestParam Instant effectiveAt) {
        return references.baseRates(currency, effectiveAt).stream().map(BaseRateResponse::from).toList();
    }
    @PostMapping("/api/v1/product-spreads") @ResponseStatus(HttpStatus.CREATED)
    void createSpread(@Valid @RequestBody ProductSpreadRequest request) {
        references.recordProductSpread(
                request.productType(), request.monthlySpread().value(), request.effectiveAt(), actors.currentActor().email());
    }
    @GetMapping("/api/v1/product-spreads")
    List<ProductSpreadResponse> listSpread(@RequestParam String productType, @RequestParam Instant effectiveAt) {
        return references.productSpreads(productType, effectiveAt).stream().map(ProductSpreadResponse::from).toList();
    }

    record BaseRateRequest(
            @NotBlank String currency,
            @NotNull @Schema(type = "string", description = "Positive monthly rate at most 1.0000000000 with at most 10 fractional digits") DecimalString monthlyRate,
            @NotNull Instant effectiveAt) {
        @AssertTrue(message = "monthlyRate must be positive, at most 1.0000000000, with at most 10 fractional digits")
        boolean isMonthlyRateValid() { return validMonthlyRate(monthlyRate); }
    }
    record ProductSpreadRequest(
            @NotBlank @Size(max = 50) String productType,
            @NotNull @Schema(type = "string", description = "Positive monthly spread at most 1.0000000000 with at most 10 fractional digits") DecimalString monthlySpread,
            @NotNull Instant effectiveAt) {
        @AssertTrue(message = "monthlySpread must be positive, at most 1.0000000000, with at most 10 fractional digits")
        boolean isMonthlySpreadValid() { return validMonthlyRate(monthlySpread); }
    }

    @Schema(
            name = "BaseRate",
            requiredProperties = {"currency", "monthlyRate", "effectiveAt", "createdBy"})
    record BaseRateResponse(String currency, String monthlyRate, Instant effectiveAt, String createdBy) {
        static BaseRateResponse from(ReferenceRateService.BaseRate value) {
            return new BaseRateResponse(
                    value.currency(), value.monthlyRate().toPlainString(), value.effectiveAt(), value.createdBy());
        }
    }

    @Schema(
            name = "ProductSpread",
            requiredProperties = {"productType", "monthlySpread", "effectiveAt", "createdBy"})
    record ProductSpreadResponse(
            String productType, String monthlySpread, Instant effectiveAt, String createdBy) {
        static ProductSpreadResponse from(ReferenceRateService.ProductSpread value) {
            return new ProductSpreadResponse(
                    value.productType(), value.monthlySpread().toPlainString(), value.effectiveAt(), value.createdBy());
        }
    }

    private static boolean validMonthlyRate(DecimalString decimal) {
        if (decimal == null) return true;
        var value = decimal.value();
        return value.signum() > 0
                && value.compareTo(new java.math.BigDecimal("1.0000000000")) <= 0
                && value.scale() <= 10;
    }
}
