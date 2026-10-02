package com.delivery.auth_service.service;

import com.delivery.auth.application.api.SessionPort;
import com.delivery.auth.domain.model.Session;
import com.delivery.auth_service.entity.AuthSession;
import com.delivery.auth_service.repository.AuthSessionRepository;
import com.delivery.auth_service.repository.AuthAccountRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaAuthSessionAdapter implements SessionPort {
    private final AuthSessionRepository sessions;
    private final AuthAccountRepository accounts;
    @Override public Session save(Session s) {
        AuthSession entity = s.id() == null ? new AuthSession()
                : sessions.findById(s.id()).orElseThrow(() -> new IllegalStateException("Auth session disappeared"));
        entity.setAuthAccount(accounts.getReferenceById(s.accountId()));
        entity.setDeviceId(s.deviceId()); entity.setDeviceName(s.deviceName());
        entity.setDeviceType(s.deviceType() == null ? null : AuthSession.DeviceType.valueOf(s.deviceType().name()));
        entity.setIpAddress(s.ipAddress()); entity.setTokenFamilyId(s.tokenFamilyId());
        entity.setIsActive(s.isActive()); entity.setLastLoginAt(s.lastLoginAt()); entity.setExpiresAt(s.expiresAt());
        return toDomain(sessions.save(entity));
    }
    @Override public List<Session> findByAccountAndDeviceForUpdate(Long accountId, String deviceId) {
        return sessions.findAccountDeviceSessionsForUpdate(accountId, deviceId).stream().map(JpaAuthSessionAdapter::toDomain).toList();
    }
    @Override @Transactional(readOnly = true)
    public List<Session> findActiveByAccount(Long accountId, LocalDateTime now, int limit) {
        return sessions.findActiveUnexpiredByAuthAccount(accounts.getReferenceById(accountId), now, PageRequest.of(0, limit))
                .stream().map(JpaAuthSessionAdapter::toDomain).toList();
    }
    @Override public int deactivateAllForAccount(Long accountId, LocalDateTime at) {
        return sessions.deactivateAllActiveSessions(accountId, at);
    }
    @Override public int deactivateForAccountAndDevice(Long accountId, String deviceId, LocalDateTime at) {
        return sessions.deactivateActiveSessionsForDevice(accountId, deviceId, at);
    }
    static Session toDomain(AuthSession s) {
        return new Session(s.getId(), s.getAuthAccount().getId(), s.getDeviceId(), s.getDeviceName(),
                s.getDeviceType() == null ? null : Session.DeviceType.valueOf(s.getDeviceType().name()),
                s.getIpAddress(), s.getTokenFamilyId(), s.getIsActive(), s.getLastLoginAt(), s.getExpiresAt(), s.getCreatedAt());
    }
}
