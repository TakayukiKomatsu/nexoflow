package com.srm.creditengine.currency.api;

import com.srm.creditengine.currency.application.CurrencyService;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/conversions")
class ConversionController {
    private final CurrencyService currency;
    ConversionController(CurrencyService currency) { this.currency = currency; }
    @GetMapping CurrencyService.Conversion convert(@RequestParam String base, @RequestParam String quote, @RequestParam @DecimalMin(value="0", inclusive=false) BigDecimal amount, @RequestParam Instant at) { return currency.resolveConversion(base, quote, amount, at); }
}
