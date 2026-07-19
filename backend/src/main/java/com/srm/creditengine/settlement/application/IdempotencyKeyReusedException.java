package com.srm.creditengine.settlement.application;

public class IdempotencyKeyReusedException extends RuntimeException {
    public IdempotencyKeyReusedException() { super("The idempotency key was already used for a different request."); }
}
