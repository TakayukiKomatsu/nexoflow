package com.srm.creditengine.assignor.application;

import com.srm.creditengine.assignor.domain.TaxId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application orchestration for assignors. Contains no JDBC or SQL; delegates persistence to
 * {@link AssignorRepository}.
 */
@Service
class AssignorApplicationService implements AssignorService {

    private final AssignorRepository repository;
    private final Clock clock;

    AssignorApplicationService(AssignorRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Assignor create(CreateCommand command) {
        UUID id = command.id() == null ? UUID.randomUUID() : command.id();
        String taxId = TaxId.normalize(command.taxId());
        Instant now = clock.instant();
        repository.save(new com.srm.creditengine.assignor.domain.Assignor(
                id, command.legalName(), taxId, command.active(), now, command.actor()));
        return new Assignor(id, command.legalName(), taxId, command.active(), now);
    }

    @Override
    public Assignor get(UUID id) {
        return repository.findById(id)
                .map(a -> new Assignor(a.id(), a.legalName(), a.taxId(), a.active(), a.createdAt()))
                .orElseThrow(() -> new IllegalArgumentException("Assignor not found"));
    }

    @Override
    public List<Assignor> list() {
        return repository.findAll().stream()
                .map(a -> new Assignor(a.id(), a.legalName(), a.taxId(), a.active(), a.createdAt()))
                .toList();
    }
}
