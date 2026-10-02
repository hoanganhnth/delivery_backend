package com.delivery.auth.domain.policy;

public class InvalidAuthToken extends RuntimeException {
    public InvalidAuthToken(String message) { super(message); }
}
