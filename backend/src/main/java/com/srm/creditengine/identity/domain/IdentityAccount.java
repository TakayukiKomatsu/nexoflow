package com.srm.creditengine.identity.domain;

import java.util.List;
import java.util.UUID;

public record IdentityAccount(UUID id, String email, String passwordHash, List<String> roles) {
    public IdentityAccount {
        roles = List.copyOf(roles);
    }
}
