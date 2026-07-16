package com.srm.creditengine.identity.application;

import com.srm.creditengine.identity.domain.IdentityAccount;

public interface TokenIssuer {
    String issue(IdentityAccount account);
}
