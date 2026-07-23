package com.srm.creditengine.assignor.api;

import com.srm.creditengine.assignor.application.AssignorService;
import com.srm.creditengine.identity.application.ActorContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/assignors")
class AssignorController {
    private final AssignorService service; private final ActorContext actors;
    AssignorController(AssignorService service, ActorContext actors) { this.service = service; this.actors = actors; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) Response create(@Valid @RequestBody Request request) { return Response.from(service.create(new AssignorService.CreateCommand(request.id(), request.legalName(), request.taxId(), request.active(), actors.currentActor().email()))); }
    @GetMapping List<Response> list() { return service.list().stream().map(Response::from).toList(); }
    @GetMapping("/{id}") Response get(@PathVariable UUID id) { return Response.from(service.get(id)); }
    @Schema(name = "AssignorRequest", requiredProperties = {"legalName", "taxId", "active"})
    record Request(
            UUID id,
            @NotBlank @Size(min = 1, max = 200) String legalName,
            @NotBlank @Size(min = 1, max = 32) String taxId,
            boolean active) {}

    @Schema(
            name = "AssignorResponse",
            requiredProperties = {"id", "legalName", "taxId", "active", "createdAt"})
    record Response(UUID id, String legalName, String taxId, boolean active, Instant createdAt) {
        static Response from(AssignorService.Assignor assignor) {
            return new Response(
                    assignor.id(),
                    assignor.legalName(),
                    assignor.taxId(),
                    assignor.active(),
                    assignor.createdAt());
        }
    }
}
