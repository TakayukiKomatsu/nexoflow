package com.srm.creditengine.reporting.api;

import com.srm.creditengine.reporting.application.SettlementStatementService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
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
            @RequestParam(required = false) UUID assignorId, @RequestParam(required = false) String assetCurrency,
            @RequestParam(required = false) String settlementCurrency, @RequestParam(required = false) String productType,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
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
            UUID reversalId,
            UUID assignorId,
            String assetCurrency,
            String settlementCurrency,
            String productType,
            UUID receivableId) {
        static EntryResponse from(SettlementStatementService.Entry e) { return new EntryResponse(e.entryId(), e.entryType(), e.signedAmount().toPlainString(), e.effectiveAt(), e.settlementId(), e.reversalId(), e.assignorId(), e.assetCurrency(), e.settlementCurrency(), e.productType(), e.receivableId()); }
    }
}
