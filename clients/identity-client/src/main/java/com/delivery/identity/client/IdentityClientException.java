package com.delivery.identity.client;

public final class IdentityClientException extends RuntimeException {

    private final IdentityClientFailure kind;

    public IdentityClientException(IdentityClientFailure kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public IdentityClientFailure kind() {
        return kind;
    }
}
