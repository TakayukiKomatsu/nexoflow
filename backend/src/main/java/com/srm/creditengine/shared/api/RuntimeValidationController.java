package com.srm.creditengine.shared.api;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/runtime")
class RuntimeValidationController {
    @GetMapping("/validation")
    Map<String, String> validate(@RequestParam @NotBlank String value) {
        return Map.of("value", value);
    }
}
