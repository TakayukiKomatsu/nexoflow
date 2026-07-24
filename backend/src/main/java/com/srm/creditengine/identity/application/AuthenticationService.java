package com.srm.creditengine.identity.application;

import com.srm.creditengine.shared.api.InvalidCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final IdentityAccountRepository accounts;
    private final PasswordVerifier passwords;
    private final TokenIssuer tokens;
    private final LoginRateLimiter rateLimiter;

    public AuthenticationService(
            IdentityAccountRepository accounts,
            PasswordVerifier passwords,
            TokenIssuer tokens,
            LoginRateLimiter rateLimiter) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
    }

    public AccessToken authenticate(String email, String password, String source) {
        rateLimiter.check(email, source);
        var account = accounts.findEnabledByEmail(email).orElse(null);
        String passwordHash = account == null ? DUMMY_PASSWORD_HASH : account.passwordHash();
        boolean passwordMatches = passwords.matches(password, passwordHash);
        if (account == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        rateLimiter.successful(email, source);
        return new AccessToken(tokens.issue(account), "Bearer", 900);
    }

    public record AccessToken(String accessToken, String tokenType, long expiresIn) {}
}
