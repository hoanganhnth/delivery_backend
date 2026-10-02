package com.delivery.auth.domain.policy;

public final class CredentialsRejected extends RuntimeException {
    public CredentialsRejected(String message) { super(message); }
}
