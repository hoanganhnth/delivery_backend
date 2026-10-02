package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.*;
import com.delivery.auth.domain.policy.AuthResourceMissing;
import java.time.LocalDateTime;
import java.util.*;

public final class DefaultDeviceSessionUseCase implements DeviceSessionUseCase {
    private final AuthAccountPort accounts;
    private final SessionPort sessions;
    private final SessionCredentialRevocationPort credentials;
    private final AuthTransactionPort transactions;
    public DefaultDeviceSessionUseCase(AuthAccountPort accounts, SessionPort sessions,
            SessionCredentialRevocationPort credentials, AuthTransactionPort transactions) {
        this.accounts = Objects.requireNonNull(accounts);
        this.sessions = Objects.requireNonNull(sessions);
        this.credentials = Objects.requireNonNull(credentials);
        this.transactions = Objects.requireNonNull(transactions);
    }
    @Override public List<Session> activeSessions(String email) {
        var account = requireAccount(email);
        return sessions.findActiveByAccount(account.id(), LocalDateTime.now(), 100);
    }
    @Override public void revokeDevice(String email, String deviceId) {
        transactions.required(() -> {
            if (deviceId == null || deviceId.isBlank()) throw new IllegalArgumentException("Device ID must not be empty");
            var account = requireAccount(email);
            LocalDateTime now = LocalDateTime.now();
            for (Session session : sessions.findByAccountAndDeviceForUpdate(account.id(), deviceId.trim())) {
                credentials.revokeFamily(session.id(), now);
                sessions.save(new Session(session.id(), session.accountId(), session.deviceId(), session.deviceName(),
                        session.deviceType(), session.ipAddress(), session.tokenFamilyId(), false,
                        session.lastLoginAt(), now, session.createdAt()));
            }
            return null;
        });
    }
    private AuthAccount requireAccount(String email) {
        String canonical = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return accounts.findByEmail(canonical)
                .orElseThrow(() -> new AuthResourceMissing("Account not found with email: " + email));
    }
}
