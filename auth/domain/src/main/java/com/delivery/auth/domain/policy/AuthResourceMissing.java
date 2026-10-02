package com.delivery.auth.domain.policy;

public final class AuthResourceMissing extends RuntimeException {
    public AuthResourceMissing(String message) { super(message); }
}
