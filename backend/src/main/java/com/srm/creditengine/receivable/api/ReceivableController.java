package com.srm.creditengine.receivable.api;

import com.srm.creditengine.identity.application.ActorContext;
import com.srm.creditengine.receivable.application.ReceivableService;
import com.srm.creditengine.shared.api.DecimalString;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/receivables")
class ReceivableController {
    private final ReceivableService service; private final ActorContext actors;
    ReceivableController(ReceivableService service, ActorContext actors) { this.service = service; this.actors = actors; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) Response create(@Valid @RequestBody Request r) { return Response.from(service.register(new ReceivableService.RegisterCommand(r.id(),r.assignorId(),r.productType(),r.faceAmount().value(),r.faceCurrency(),r.issueDate(),r.dueDate(),actors.currentActor().email()))); }
    @GetMapping List<Response> list() { return service.list().stream().map(Response::from).toList(); }
    @GetMapping("/{id}") Response get(@PathVariable UUID id) { return Response.from(service.get(id)); }
    @Schema(
            name = "ReceivableRequest",
            requiredProperties = {
                "assignorId", "productType", "faceAmount", "faceCurrency", "issueDate", "dueDate"
            })
    record Request(
            UUID id,
            @NotNull UUID assignorId,
            @NotBlank @Size(min = 1, max = 50)
                    @Schema(allowableValues = {"MERCANTILE_INVOICE", "POST_DATED_CHEQUE"}) String productType,
            @NotNull DecimalString faceAmount,
            @NotBlank @Size(min = 1, max = 3) @Schema(allowableValues = {"BRL", "USD"}) String faceCurrency,
            @NotNull LocalDate issueDate,
            @NotNull @Schema(description = "Due date no more than ten years after the issue date") LocalDate dueDate) {
        @AssertTrue(message = "faceAmount must be positive with no more than 15 integer digits and 4 fractional digits")
        boolean isFaceAmountValid() {
            if (faceAmount == null) return true;
            BigDecimal value = faceAmount.value();
            return value.signum() > 0 && value.scale() <= 4 && value.precision() - value.scale() <= 15;
        }
    }
    @Schema(name = "ReceivableResponse", requiredProperties = {
        "id", "assignorId", "productType", "faceAmount", "faceCurrency",
        "issueDate", "dueDate", "status", "version"
    })
    record Response(
                    UUID id,
                    UUID assignorId,
                    @Schema(allowableValues = {"MERCANTILE_INVOICE", "POST_DATED_CHEQUE"}) String productType,
                    String faceAmount,
                    @Schema(allowableValues = {"BRL", "USD"}) String faceCurrency,
                    LocalDate issueDate,
                    LocalDate dueDate,
                    @Schema(allowableValues = {"REGISTERED", "SETTLED", "REVERSED"}) String status,
                    long version) {
        static Response from(ReceivableService.Receivable value) {
            return new Response(value.id(), value.assignorId(), value.productType(),
                    value.faceAmount().setScale(4, RoundingMode.HALF_EVEN).toPlainString(),
                    value.faceCurrency(), value.issueDate(), value.dueDate(),
                    value.status(), value.version());
        }
    }
}
