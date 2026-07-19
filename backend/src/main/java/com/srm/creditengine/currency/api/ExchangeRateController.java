package com.srm.creditengine.currency.api;

import com.srm.creditengine.currency.application.CurrencyService;
import com.srm.creditengine.identity.application.ActorContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
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
    void create(@Valid @RequestBody Request request) { currency.recordObservation(request.base(), request.quote(), request.rate(), request.source(), request.observedAt(), actor.currentActor().email()); }
    @GetMapping
    List<CurrencyService.Observation> list(@RequestParam String base, @RequestParam String quote) { return currency.observations(base, quote); }
    record Request(@NotBlank String base, @NotBlank String quote, @NotNull @DecimalMin(value="0", inclusive=false) BigDecimal rate, @NotBlank String source, @NotNull Instant observedAt) {}
}
