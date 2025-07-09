package com.delivery.auth_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.entity.AuthSession;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findByRefreshToken(String refreshToken);

    List<AuthSession> findByAuthAccount(AuthAccount account);

    // ✅ Thêm dòng này để fix lỗi
    List<AuthSession> findByAuthAccountAndDeviceIdAndIsActiveTrue(AuthAccount account, String deviceId);
}
