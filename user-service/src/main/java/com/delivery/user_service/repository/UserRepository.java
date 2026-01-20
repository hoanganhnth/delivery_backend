package com.delivery.user_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.delivery.user_service.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByAuthId(Long authId);

    long countByRole(String role);

    List<User> findAllByOrderByCreatedAtDesc();

    List<User> findByIsBlocked(Boolean isBlocked);

    long countByIsActive(Boolean isActive);

    long countByIsBlocked(Boolean isBlocked);
}
