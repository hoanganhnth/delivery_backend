package com.delivery.auth.application.api;

/** Password hashing/verification adapter boundary. */
public interface PasswordCredentialPort {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
