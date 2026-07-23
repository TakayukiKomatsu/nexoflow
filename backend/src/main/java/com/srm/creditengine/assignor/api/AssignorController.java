package com.srm.creditengine.assignor.api;

import com.srm.creditengine.assignor.application.AssignorService;
import com.srm.creditengine.identity.application.ActorContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/assignors")
class AssignorController {
    private final AssignorService service; private final ActorContext actors;
    AssignorController(AssignorService service, ActorContext actors) { this.service = service; this.actors = actors; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) AssignorService.Assignor create(@Valid @RequestBody Request request) { return service.create(new AssignorService.CreateCommand(request.id(), request.legalName(), request.taxId(), request.active(), actors.currentActor().email())); }
    @GetMapping List<AssignorService.Assignor> list() { return service.list(); }
    @GetMapping("/{id}") AssignorService.Assignor get(@PathVariable UUID id) { return service.get(id); }
    record Request(
            UUID id,
            @NotBlank @Size(max = 200) String legalName,
            @NotBlank @Size(max = 32) String taxId,
            boolean active) {}
}
