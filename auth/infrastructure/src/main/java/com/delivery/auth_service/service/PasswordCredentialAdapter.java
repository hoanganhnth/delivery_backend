package com.delivery.auth_service.service;

import com.delivery.auth.application.api.PasswordCredentialPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordCredentialAdapter implements PasswordCredentialPort {
    private final PasswordEncoder passwords;
    @Override public String hash(String rawPassword) { return passwords.encode(rawPassword); }
    @Override public boolean matches(String rawPassword, String passwordHash) { return passwords.matches(rawPassword, passwordHash); }
}
