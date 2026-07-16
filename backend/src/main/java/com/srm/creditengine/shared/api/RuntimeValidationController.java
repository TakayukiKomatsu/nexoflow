package com.srm.creditengine.shared.api;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Profile("test")
@RequestMapping("/api/v1/runtime")
class RuntimeValidationController {
    @GetMapping("/validation")
    Map<String, String> validate(@RequestParam @NotBlank String value) {
        return Map.of("value", value);
    }

    @GetMapping("/failure")
    Map<String, String> fail() {
        throw new IllegalStateException("database-password=must-not-leak");
    }

    @PostMapping(value = "/echo", consumes = "application/json")
    Map<String, String> echo(@RequestBody Map<String, String> body) {
        return body;
    }
}
