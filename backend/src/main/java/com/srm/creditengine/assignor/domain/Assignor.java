package com.srm.creditengine.assignor.domain;

import java.time.Instant;
import java.util.UUID;

/** Pure domain entity representing an assignor. */
public record Assignor(
        UUID id,
        String legalName,
        String taxId,
        boolean active,
        Instant createdAt,
        String createdBy) {
    public Assignor {
        if (legalName == null || legalName.isBlank() || legalName.length() > 200) {
            throw new IllegalArgumentException("Assignor legal name must not exceed 200 characters");
        }
        if (createdBy == null || createdBy.isBlank() || createdBy.length() > 320) {
            throw new IllegalArgumentException("Assignor actor must not exceed 320 characters");
        }
    }
}
