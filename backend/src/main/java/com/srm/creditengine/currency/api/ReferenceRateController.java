package com.srm.creditengine.currency.api;

import com.srm.creditengine.currency.application.ReferenceRateService;
import com.srm.creditengine.identity.application.ActorContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
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
                request.currency(), request.monthlyRate(), request.effectiveAt(), actors.currentActor().email());
    }
    @GetMapping("/api/v1/base-rates")
    List<ReferenceRateService.BaseRate> listBase(@RequestParam String currency, @RequestParam Instant effectiveAt) { return references.baseRates(currency, effectiveAt); }
    @PostMapping("/api/v1/product-spreads") @ResponseStatus(HttpStatus.CREATED)
    void createSpread(@Valid @RequestBody ProductSpreadRequest request) {
        references.recordProductSpread(
                request.productType(), request.monthlySpread(), request.effectiveAt(), actors.currentActor().email());
    }
    @GetMapping("/api/v1/product-spreads")
    List<ReferenceRateService.ProductSpread> listSpread(@RequestParam String productType, @RequestParam Instant effectiveAt) { return references.productSpreads(productType, effectiveAt); }

    record BaseRateRequest(
            @NotBlank String currency,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1.0000000000") BigDecimal monthlyRate,
            @NotNull Instant effectiveAt) {}
    record ProductSpreadRequest(
            @NotBlank String productType,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1.0000000000") BigDecimal monthlySpread,
            @NotNull Instant effectiveAt) {}
}
