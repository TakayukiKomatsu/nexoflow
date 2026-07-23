package com.srm.creditengine.receivable.application;

import com.srm.creditengine.receivable.domain.ReceivableRegistration;
import com.srm.creditengine.shared.domain.DomainResourceNotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application orchestration for receivables. Contains no JDBC or SQL; delegates persistence to
 * {@link ReceivableRepository} and assignor status checks to {@link AssignorStatusReader}.
 */
@Service
class ReceivableApplicationService implements ReceivableService {

    private final ReceivableRepository receivableRepository;
    private final AssignorStatusReader assignorStatusReader;
    private final Clock clock;

    ReceivableApplicationService(
            ReceivableRepository receivableRepository,
            AssignorStatusReader assignorStatusReader,
            Clock clock) {
        this.receivableRepository = receivableRepository;
        this.assignorStatusReader = assignorStatusReader;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReceivableService.Receivable register(RegisterCommand c) {
        ReceivableRegistration.validate(new ReceivableRegistration.RegisterCommand(
                c.id(), c.assignorId(), c.productType(), c.faceAmount(),
                c.faceCurrency(), c.issueDate(), c.dueDate(), c.actor()));
        if (!assignorStatusReader.isActive(c.assignorId())) {
            throw new IllegalArgumentException("Receivable requires an active assignor");
        }
        UUID id = c.id() == null ? UUID.randomUUID() : c.id();
        // Use fully-qualified name to avoid ambiguity with the ReceivableService.Receivable
        // nested record that is in scope via the implemented interface.
        receivableRepository.save(new com.srm.creditengine.receivable.domain.Receivable(
                id, c.assignorId(), c.productType(), c.faceAmount(), c.faceCurrency(),
                c.issueDate(), c.dueDate(), "REGISTERED", 0L, clock.instant(), c.actor()));
        return new ReceivableService.Receivable(
                id, c.assignorId(), c.productType(), c.faceAmount(), c.faceCurrency(),
                c.issueDate(), c.dueDate(), "REGISTERED", 0L);
    }

    @Override
    public ReceivableService.Receivable get(UUID id) {
        return receivableRepository.findById(id)
                .map(ReceivableApplicationService::toServiceRecord)
                .orElseThrow(DomainResourceNotFoundException::new);
    }

    @Override
    public List<ReceivableService.Receivable> list() {
        return receivableRepository.findAll().stream()
                .map(ReceivableApplicationService::toServiceRecord)
                .toList();
    }

    private static ReceivableService.Receivable toServiceRecord(
            com.srm.creditengine.receivable.domain.Receivable r) {
        return new ReceivableService.Receivable(
                r.id(), r.assignorId(), r.productType(), r.faceAmount(),
                r.faceCurrency(), r.issueDate(), r.dueDate(), r.status(), r.version());
    }
}
