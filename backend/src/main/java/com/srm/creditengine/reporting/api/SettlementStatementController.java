package com.srm.creditengine.reporting.api;

import com.srm.creditengine.reporting.application.SettlementStatementService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SettlementStatementController {
    private final SettlementStatementService statements;
    SettlementStatementController(SettlementStatementService statements) { this.statements = statements; }
    @GetMapping("/api/v1/settlement-statements")
    PageResponse statements(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID assignorId,
            @Parameter(schema = @Schema(allowableValues = {"BRL", "USD"}))
            @RequestParam(required = false) String assetCurrency,
            @Parameter(schema = @Schema(allowableValues = {"BRL", "USD"}))
            @RequestParam(required = false) String settlementCurrency,
            @Parameter(schema = @Schema(allowableValues = {"MERCANTILE_INVOICE", "POST_DATED_CHEQUE"}))
            @RequestParam(required = false) String productType,
            @Parameter(description = "Zero-based page; page multiplied by size must not exceed 10000.")
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @Parameter(description = "Page size from 1 through 100.")
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        var result = statements.query(new SettlementStatementService.Filter(from, to, assignorId, assetCurrency, settlementCurrency, productType, page, size));
        return new PageResponse(result.entries().stream().map(EntryResponse::from).toList(), result.page(), result.size(), result.hasNext());
    }
    @Schema(requiredProperties = {"entries", "page", "size", "hasNext"})
    record PageResponse(java.util.List<EntryResponse> entries, int page, int size, boolean hasNext) {}
    @Schema(requiredProperties = {
        "entryId", "entryType", "signedAmount", "effectiveAt", "settlementId", "reversalId",
        "assignorId", "assetCurrency", "settlementCurrency", "productType", "receivableId"
    })
    record EntryResponse(
            UUID entryId,
            @Schema(allowableValues = {"SETTLEMENT", "REVERSAL"}) String entryType,
            String signedAmount,
            Instant effectiveAt,
            UUID settlementId,
            @Schema(types = {"string", "null"}, format = "uuid") UUID reversalId,
            UUID assignorId,
            @Schema(allowableValues = {"BRL", "USD"}) String assetCurrency,
            @Schema(allowableValues = {"BRL", "USD"}) String settlementCurrency,
            @Schema(allowableValues = {"MERCANTILE_INVOICE", "POST_DATED_CHEQUE"}) String productType,
            UUID receivableId) {
        static EntryResponse from(SettlementStatementService.Entry e) { return new EntryResponse(e.entryId(), e.entryType(), money(e.signedAmount()), e.effectiveAt(), e.settlementId(), e.reversalId(), e.assignorId(), e.assetCurrency(), e.settlementCurrency(), e.productType(), e.receivableId()); }
    }
    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }
}
