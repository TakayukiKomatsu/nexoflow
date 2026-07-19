package com.srm.creditengine.currency.api;

import com.srm.creditengine.currency.application.CurrencyService;
import com.srm.creditengine.currency.application.FxSynchronizationService;
import com.srm.creditengine.identity.application.ActorContext;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/fx-sync")
class FxSynchronizationController {
    private final FxSynchronizationService synchronization;
    private final ActorContext actor;
    FxSynchronizationController(FxSynchronizationService synchronization, ActorContext actor) { this.synchronization = synchronization; this.actor = actor; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    CurrencyService.Observation synchronize(@RequestParam @NotBlank String base, @RequestParam @NotBlank String quote) {
        return synchronization.synchronize(base, quote, actor.currentActor().email());
    }
}
