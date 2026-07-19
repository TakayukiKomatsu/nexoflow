package com.srm.creditengine.receivable.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ReceivableService {
    Receivable register(RegisterCommand command);
    Receivable get(UUID id);
    List<Receivable> list();
    record RegisterCommand(UUID id, UUID assignorId, String productType, BigDecimal faceAmount, String faceCurrency, LocalDate issueDate, LocalDate dueDate, String actor) {}
    record Receivable(UUID id, UUID assignorId, String productType, BigDecimal faceAmount, String faceCurrency, LocalDate issueDate, LocalDate dueDate, String status, long version) {}
}
