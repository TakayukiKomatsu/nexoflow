package com.srm.creditengine.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.srm.creditengine.shared.api.InvalidCredentialsException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuthenticationServiceTest {
    @Test
    void missingAccountsStillExecuteThePasswordVerificationPath() {
        IdentityAccountRepository accounts = mock(IdentityAccountRepository.class);
        PasswordVerifier passwords = mock(PasswordVerifier.class);
        TokenIssuer tokens = mock(TokenIssuer.class);
        LoginRateLimiter rateLimiter = mock(LoginRateLimiter.class);
        when(accounts.findEnabledByEmail("missing@srm.local")).thenReturn(Optional.empty());
        when(passwords.matches(anyString(), anyString())).thenReturn(false);
        AuthenticationService authentication =
                new AuthenticationService(accounts, passwords, tokens, rateLimiter);

        assertThatThrownBy(() -> authentication.authenticate(
                        "missing@srm.local", "candidate-secret", "192.0.2.20"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwords).matches(eq("candidate-secret"), anyString());
    }
}
