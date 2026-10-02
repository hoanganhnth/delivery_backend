package com.delivery.auth_service.repository;

import com.delivery.auth_service.entity.IdentityRegistration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentityRegistrationRepository extends JpaRepository<IdentityRegistration, Long> {
    Optional<IdentityRegistration> findByHandleHash(String handleHash);

    /**
     * The handle is recovery metadata, not an account record. Keeping it only
     * for the configured post-expiry retention bounds PII-adjacent metadata and
     * prevents the registration rollout from creating an unbounded table.
     */
    @Modifying
    @Query("delete from IdentityRegistration registration where registration.expiresAt < :expiredBefore")
    int deleteExpiredBefore(@Param("expiredBefore") LocalDateTime expiredBefore);
}
