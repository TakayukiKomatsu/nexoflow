package com.srm.creditengine.assignor.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AssignorService {
    Assignor create(CreateCommand command);
    Assignor get(UUID id);
    List<Assignor> list();

    record CreateCommand(UUID id, String legalName, String taxId, boolean active, String actor) {}
    record Assignor(UUID id, String legalName, String taxId, boolean active, Instant createdAt) {}
}
