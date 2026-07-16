package com.srm.creditengine.identity.application;

public interface PasswordVerifier {
    boolean matches(String rawPassword, String encodedPassword);
}
