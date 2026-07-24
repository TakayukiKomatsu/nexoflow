package com.srm.creditengine.currency.api;

import com.srm.creditengine.currency.application.CurrencyService;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/exchange-rates")
class ExchangeRateController {
    private final CurrencyService currency; private final ActorContext actor;
    ExchangeRateController(CurrencyService currency, ActorContext actor) { this.currency = currency; this.actor = actor; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    void create(@Valid @RequestBody Request request) { currency.recordObservation(request.base(), request.quote(), request.rate().value(), request.source(), request.observedAt(), actor.currentActor().email()); }
    @GetMapping
    List<CurrencyApiResponse.Observation> list(
            @RequestParam @Schema(allowableValues = {"BRL", "USD"}) String base,
            @RequestParam @Schema(allowableValues = {"BRL", "USD"}) String quote) {
        return currency.observations(base, quote).stream().map(CurrencyApiResponse::observation).toList();
    }
    @Schema(
            name = "ExchangeRateRequest",
            requiredProperties = {"base", "quote", "rate", "source", "observedAt"})
    record Request(
            @NotBlank @Schema(allowableValues = {"BRL", "USD"}) String base,
            @NotBlank @Schema(allowableValues = {"BRL", "USD"}) String quote,
            @NotNull @Schema(type = "string", description = "Positive rate with at most 9 integer and 10 fractional digits") DecimalString rate,
            @NotBlank @Size(min = 1, max = 50) String source,
            @NotNull Instant observedAt) {
        @AssertTrue(message = "rate must be positive with at most 9 integer and 10 fractional digits")
        boolean isRateValid() {
            if (rate == null) return true;
            var value = rate.value();
            return value.signum() > 0
                    && value.scale() <= 10
                    && value.precision() - value.scale() <= 9;
        }
    }
}
