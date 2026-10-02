package com.delivery.auth.domain.policy;

public final class RegistrationIdentityConflict extends RuntimeException {
    public RegistrationIdentityConflict(String message) { super(message); }
}
