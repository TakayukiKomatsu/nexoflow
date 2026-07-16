package com.srm.creditengine.identity.infrastructure;

import com.srm.creditengine.identity.application.PasswordVerifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class BcryptPasswordVerifier implements PasswordVerifier {
    private final PasswordEncoder encoder;

    BcryptPasswordVerifier(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}
