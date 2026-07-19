package com.srm.creditengine.identity.application;

import java.util.Set;
import java.util.UUID;

public record CurrentActor(UUID id, String email, Set<ActorRole> roles) {
    public CurrentActor {
        roles = Set.copyOf(roles);
    }
}
