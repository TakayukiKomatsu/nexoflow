package com.srm.creditengine.audit.api;

import com.srm.creditengine.audit.application.AuditEventQuery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Restricted inspection endpoint for append-only, redacted audit metadata. */
@RestController
class AuditEventController {
    private final AuditEventQuery query;

    AuditEventController(AuditEventQuery query) {
        this.query = query;
    }

    @GetMapping("/api/v1/audit-events")
    List<EventResponse> events(@RequestParam(defaultValue = "50") int size) {
        if (size < 1 || size > 100) throw new IllegalArgumentException("size is out of bounds");
        return query.latest(size).stream().map(EventResponse::from).toList();
    }
    @Schema(requiredProperties = {
        "id", "actor", "action", "targetType", "targetId", "occurredAt", "correlationId", "safeMetadata"
    })
    record EventResponse(
            UUID id,
            String actor,
            String action,
            String targetType,
            UUID targetId,
            Instant occurredAt,
            @Schema(types = {"string", "null"}) String correlationId,
            String safeMetadata) {
        static EventResponse from(AuditEventQuery.Event event) {
            return new EventResponse(
                    event.id(),
                    event.actor(),
                    event.action(),
                    event.targetType(),
                    event.targetId(),
                    event.occurredAt(),
                    event.correlationId(),
                    event.safeMetadata());
        }
    }
}
