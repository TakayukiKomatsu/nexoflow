package com.srm.creditengine.shared.api;

public final class LoginRateLimitedException extends RuntimeException {
    public LoginRateLimitedException() {
        super("Too many login attempts. Try again later.");
    }
}
