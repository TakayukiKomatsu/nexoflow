package com.srm.creditengine.settlement.api;

import com.srm.creditengine.identity.application.ActorContext;
import com.srm.creditengine.settlement.application.SettlementService;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
class SettlementController {
    private final SettlementService settlements;
    private final ActorContext actors;

    SettlementController(SettlementService settlements, ActorContext actors) { this.settlements = settlements; this.actors = actors; }

    @PostMapping("/api/v1/settlement-previews")
    PreviewResponse preview(@Valid @RequestBody QuoteIdsRequest request) {
        return PreviewResponse.from(settlements.preview(request.quoteIds(), actors.currentActor().email()));
    }

    @PostMapping("/api/v1/settlements")
    @ApiResponse(
            responseCode = "201",
            description = "Settlement created, or the original result replayed.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SettlementResponse.class)),
            headers = @Header(
                    name = "Idempotent-Replay",
                    description = "Present with value true only when returning an idempotent replay.",
                    schema = @Schema(type = "string", allowableValues = "true")))
    ResponseEntity<SettlementResponse> settle(@Valid @RequestBody QuoteIdsRequest request,
            @Parameter(schema = @Schema(minLength = 1, maxLength = 200))
            @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 1, max = 200) String key) {
        var result = settlements.settle(request.quoteIds(), key, actors.currentActor().email());
        var headers = new HttpHeaders();
        if (result.replayed()) headers.set("Idempotent-Replay", "true");
        return new ResponseEntity<>(SettlementResponse.from(result), headers, HttpStatus.CREATED);
    }
    @GetMapping("/api/v1/settlements/{settlementId}")
    SettlementResponse get(@PathVariable UUID settlementId) {
        return SettlementResponse.from(settlements.get(settlementId));
    }
    @PostMapping("/api/v1/settlements/{settlementId}/reversals")
    @ApiResponse(
            responseCode = "201",
            description = "Reversal created, or the original result replayed.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ReversalResponse.class)),
            headers = @Header(
                    name = "Idempotent-Replay",
                    description = "Present with value true only when returning an idempotent replay.",
                    schema = @Schema(type = "string", allowableValues = "true")))
    ResponseEntity<ReversalResponse> reverse(@PathVariable UUID settlementId, @Valid @RequestBody ReversalRequest request,
            @Parameter(schema = @Schema(minLength = 1, maxLength = 200))
            @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 1, max = 200) String key) {
        var reversal = settlements.reverse(settlementId, request.reason(), key, actors.currentActor().email());
        var headers = new HttpHeaders(); if (reversal.replayed()) headers.set("Idempotent-Replay", "true");
        return new ResponseEntity<>(ReversalResponse.from(reversal), headers, HttpStatus.CREATED);
    }

    record QuoteIdsRequest(
            @ArraySchema(minItems = 1, maxItems = SettlementService.MAX_QUOTE_IDS, uniqueItems = true)
            @NotEmpty @Size(min = 1, max = SettlementService.MAX_QUOTE_IDS)
                    List<@NotNull UUID> quoteIds) {}
    record ReversalRequest(
            @NotBlank @Size(min = 1, max = 500) @Schema(minLength = 1, maxLength = 500) String reason) {}
    @Schema(requiredProperties = {"quoteId", "receivableId", "settlementAmount"})
    record ItemResponse(UUID quoteId, UUID receivableId, String settlementAmount) {
        static ItemResponse from(SettlementService.Item item) { return new ItemResponse(item.quoteId(), item.receivableId(), money(item.settlementAmount())); }
    }
    @Schema(requiredProperties = {"items", "settlementCurrency", "totalAmount", "asOf", "earliestExpiry"})
    record PreviewResponse(
            List<ItemResponse> items,
            @Schema(allowableValues = {"BRL", "USD"}) String settlementCurrency,
            String totalAmount,
            Instant asOf,
            Instant earliestExpiry) {
        static PreviewResponse from(SettlementService.Preview preview) { return new PreviewResponse(preview.items().stream().map(ItemResponse::from).toList(), preview.settlementCurrency(), money(preview.totalAmount()), preview.asOf(), preview.earliestExpiry()); }
    }
    @Schema(requiredProperties = {"settlementId", "status", "items", "settlementCurrency", "totalAmount", "completedAt"})
    record SettlementResponse(
            UUID settlementId,
            @Schema(allowableValues = "COMPLETED") String status,
            List<ItemResponse> items,
            @Schema(allowableValues = {"BRL", "USD"}) String settlementCurrency,
            String totalAmount,
            Instant completedAt) {
        static SettlementResponse from(SettlementService.Result result) { return new SettlementResponse(result.settlementId(), result.status(), result.items().stream().map(ItemResponse::from).toList(), result.settlementCurrency(), money(result.totalAmount()), result.completedAt()); }
    }
    @Schema(
            name = "ReversalResponse",
            requiredProperties = {"reversalId", "settlementId", "reason", "reversedAt"})
    record ReversalResponse(UUID reversalId, UUID settlementId, String reason, Instant reversedAt) { static ReversalResponse from(SettlementService.Reversal reversal) { return new ReversalResponse(reversal.reversalId(), reversal.settlementId(), reversal.reason(), reversal.reversedAt()); } }
    private static String money(BigDecimal amount) { return amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString(); }
}
