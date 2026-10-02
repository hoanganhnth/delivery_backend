package com.delivery.auth.application.api;
public interface UserStatusProjectionPort {
    void synchronize(Long userId, Long adminId, String reason, boolean blocked);
}
