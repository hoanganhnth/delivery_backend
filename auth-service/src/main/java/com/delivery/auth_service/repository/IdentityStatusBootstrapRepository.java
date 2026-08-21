package com.delivery.auth_service.repository;

import com.delivery.auth_service.entity.IdentityStatusBootstrap;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentityStatusBootstrapRepository extends JpaRepository<IdentityStatusBootstrap, Long> {
    @Modifying
    @Query(value = "insert into identity_status_bootstrap (auth_account_id, lifecycle_version, emitted_at) "
            + "values (:accountId, :lifecycleVersion, :emittedAt) on conflict (auth_account_id) do nothing",
            nativeQuery = true)
    int claim(@Param("accountId") Long accountId,
            @Param("lifecycleVersion") Long lifecycleVersion,
            @Param("emittedAt") LocalDateTime emittedAt);
}
