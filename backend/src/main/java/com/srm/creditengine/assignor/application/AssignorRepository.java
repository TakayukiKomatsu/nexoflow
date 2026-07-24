package com.srm.creditengine.assignor.application;

import com.srm.creditengine.assignor.domain.Assignor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Application-layer port for assignor persistence. Implementations live in infrastructure. */
public interface AssignorRepository {

    void save(Assignor assignor);

    Optional<Assignor> findById(UUID id);

    List<Assignor> findAll();
}
