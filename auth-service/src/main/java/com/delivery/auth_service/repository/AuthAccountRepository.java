package com.delivery.auth_service.repository;

import com.delivery.auth_service.entity.AuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {
    Optional<AuthAccount> findByEmail(String email);

    boolean existsByEmail(String email); // ➕ bổ sung gợi ý
}
