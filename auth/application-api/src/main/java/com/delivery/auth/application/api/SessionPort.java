package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.Session;
import java.time.LocalDateTime;
import java.util.List;

/** Persistence boundary for device sessions and family revocation. */
public interface SessionPort {

    Session save(Session session);

    List<Session> findByAccountAndDeviceForUpdate(Long accountId, String deviceId);

    List<Session> findActiveByAccount(Long accountId, LocalDateTime now, int limit);

    int deactivateAllForAccount(Long accountId, LocalDateTime at);

    int deactivateForAccountAndDevice(Long accountId, String deviceId, LocalDateTime at);
}
