package com.srm.creditengine.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.srm.creditengine.assignor.application.AssignorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.creditengine.currency.application.CurrencyService;
import com.srm.creditengine.currency.application.ReferenceRateService;
import com.srm.creditengine.pricing.PricingStrategyRegistry;
import com.srm.creditengine.receivable.application.ReceivableService;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@SpringBootTest(properties = "srm.clock.fixed-instant=2030-01-15T12:00:00Z")
class PricingQuoteSnapshotTest {
    private static final Instant PRICED_AT = Instant.parse("2030-01-15T12:00:00Z");
    private static final LocalDate DUE_DATE = LocalDate.parse("2030-02-14");

    @Autowired AssignorService assignors;
    @Autowired ReceivableService receivables;
    @Autowired ReferenceRateService rates;
    @Autowired CurrencyService currency;
    @Autowired PricingStrategyRegistry strategies;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void QUOTE_005_roundTripsTheCompleteCanonicalSnapshotAndUsesTheExactExpiryBoundary() throws Exception {
        UUID assignorId = UUID.randomUUID();
        assignors.create(new AssignorService.CreateCommand(
                assignorId,
                "Snapshot Ltd",
                "SNAP" + assignorId.toString().substring(0, 8),
                true,
                "operator@srm.local"));
        UUID receivableId = UUID.randomUUID();
        receivables.register(new ReceivableService.RegisterCommand(
                receivableId,
                assignorId,
                "MERCANTILE_INVOICE",
                new BigDecimal("1000.00"),
                "BRL",
                LocalDate.parse("2030-01-01"),
                DUE_DATE,
                "operator@srm.local"));

        PricingService pricedService = serviceAt(PRICED_AT);
        var created = pricedService.createQuote(receivableId, "BRL", "operator@srm.local");
        rates.recordProductSpread(
                "MERCANTILE_INVOICE",
                new BigDecimal("0.020"),
                Instant.parse("2030-01-15T12:01:00Z"));

        var immediatelyBeforeExpiry =
                serviceAt(created.expiresAt().minusNanos(1)).getQuote(created.id());
        var exactlyAtExpiry = serviceAt(created.expiresAt()).getQuote(created.id());

        JsonNode getResponse = objectMapper.readTree(mvc.perform(get("/api/v1/pricing-quotes/{id}", created.id()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());

        assertThat(created.productType()).isEqualTo("MERCANTILE_INVOICE");
        assertThat(created.dueDate()).isEqualTo(DUE_DATE);
        assertThat(immediatelyBeforeExpiry.productType()).isEqualTo("MERCANTILE_INVOICE");
        assertThat(immediatelyBeforeExpiry.dueDate()).isEqualTo(DUE_DATE);
        assertThat(financialStrings(immediatelyBeforeExpiry.breakdown()))
                .isEqualTo(financialStrings(created.breakdown()))
                .contains("975.61");
        assertThat(getResponse.path("productType").asText()).isEqualTo(created.productType());
        assertThat(getResponse.path("dueDate").asText()).isEqualTo(created.dueDate().toString());
        assertThat(financialStrings(getResponse.path("pricing")))
                .isEqualTo(financialStrings(created.breakdown()))
                .contains("975.61");
        assertThat(immediatelyBeforeExpiry.status()).isEqualTo("ACTIVE");
        assertThat(exactlyAtExpiry.status()).isEqualTo("EXPIRED");
        assertThat(created.expiresAt()).isEqualTo(Instant.parse("2030-01-15T12:15:00Z"));
    }

    private PricingService serviceAt(Instant instant) {
        return new AuthoritativePricingService(
                rates,
                currency,
                strategies,
                receivables,
                jdbc,
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static List<String> financialStrings(PricingService.Breakdown breakdown) {
        return List.of(
                breakdown.faceAmount().toPlainString(),
                breakdown.baseRate().toPlainString(),
                breakdown.spread().toPlainString(),
                breakdown.termInMonths().toPlainString(),
                breakdown.discountedAmount().toPlainString(),
                breakdown.fxRate().toPlainString(),
                breakdown.settlementAmount().toPlainString());
    }
    private static List<String> financialStrings(JsonNode pricing) {
        return List.of(
                pricing.path("faceAmount").asText(),
                pricing.path("baseRate").asText(),
                pricing.path("spread").asText(),
                pricing.path("termInMonths").asText(),
                pricing.path("discountedAmount").asText(),
                pricing.path("fxRate").asText(),
                pricing.path("settlementAmount").asText());
    }

}
