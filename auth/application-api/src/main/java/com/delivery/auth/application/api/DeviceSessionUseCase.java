package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.Session;
import java.util.List;

public interface DeviceSessionUseCase {
    List<Session> activeSessions(String email);
    void revokeDevice(String email, String deviceId);
}
