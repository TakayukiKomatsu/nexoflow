package com.srm.creditengine.receivable.application;

import com.srm.creditengine.receivable.domain.Receivable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Application-layer port for receivable persistence. Implementations live in infrastructure. */
public interface ReceivableRepository {

    void save(Receivable receivable);

    Optional<Receivable> findById(UUID id);

    List<Receivable> findAll();
}
