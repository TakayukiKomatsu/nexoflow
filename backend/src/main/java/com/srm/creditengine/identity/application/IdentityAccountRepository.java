package com.srm.creditengine.identity.application;

import com.srm.creditengine.identity.domain.IdentityAccount;
import java.util.Optional;

public interface IdentityAccountRepository {
    Optional<IdentityAccount> findEnabledByEmail(String email);
}
