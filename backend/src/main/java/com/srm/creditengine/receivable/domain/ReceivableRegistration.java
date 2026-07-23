package com.srm.creditengine.receivable.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Pure domain service that validates a receivable registration command. */
public final class ReceivableRegistration {

    private ReceivableRegistration() {}

    /**
     * Command carrying all data required to register a new receivable.
     * Defined here (domain layer) so validation logic remains free of application/infrastructure
     * dependencies.
     */
    public record RegisterCommand(
            UUID id,
            UUID assignorId,
            String productType,
            BigDecimal faceAmount,
            String faceCurrency,
            LocalDate issueDate,
            LocalDate dueDate,
            String actor) {}

    /**
     * Validates the command; throws {@link IllegalArgumentException} if any rule is violated.
     *
     * <ul>
     *   <li>Face amount must be positive.
     *   <li>Face amount scale must not exceed four decimal places.
     *   <li>Issue date and due date must be present.
     *   <li>Due date must be strictly after issue date.
     * </ul>
     */
    public static void validate(RegisterCommand command) {
        if (command.faceAmount() == null
                || command.faceAmount().signum() <= 0
                || command.faceAmount().scale() > 4
                || command.issueDate() == null
                || command.dueDate() == null
                || !command.dueDate().isAfter(command.issueDate())) {
            throw new IllegalArgumentException(
                    "Receivable face amount must be positive with no more than four decimal places"
                            + " and due date must be after issue date");
        }
        if (command.dueDate().isAfter(command.issueDate().plusYears(10))) {
            throw new IllegalArgumentException("Receivable maturity must not exceed ten years");
        }
        if (command.actor() == null || command.actor().isBlank() || command.actor().length() > 320) {
            throw new IllegalArgumentException("Receivable actor must not exceed 320 characters");
        }
    }
}
