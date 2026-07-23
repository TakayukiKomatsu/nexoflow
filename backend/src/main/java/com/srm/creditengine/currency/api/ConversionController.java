package com.srm.creditengine.currency.api;

import com.srm.creditengine.currency.application.CurrencyService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
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
    @GetMapping
    CurrencyApiResponse.Conversion convert(
            @RequestParam String base,
            @RequestParam String quote,
            @RequestParam @DecimalMin(value="0", inclusive=false) @Pattern(regexp = "\\d+(?:\\.\\d+)?") String amount,
            @RequestParam Instant at) {
        return CurrencyApiResponse.conversion(
                currency.resolveConversion(base, quote, new BigDecimal(amount), at));
    }
}
