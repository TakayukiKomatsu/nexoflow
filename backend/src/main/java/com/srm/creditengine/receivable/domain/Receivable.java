package com.srm.creditengine.receivable.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Pure domain entity representing a receivable. */
public record Receivable(
        UUID id,
        UUID assignorId,
        String productType,
        BigDecimal faceAmount,
        String faceCurrency,
        LocalDate issueDate,
        LocalDate dueDate,
        String status,
        long version,
        Instant createdAt,
        String createdBy) {}
