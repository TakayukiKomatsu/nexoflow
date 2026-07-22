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
        String createdBy) {}
