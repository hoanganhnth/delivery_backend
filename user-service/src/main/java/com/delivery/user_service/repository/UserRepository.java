package com.delivery.user_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.delivery.user_service.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByAuthId(Long authId);

    Optional<User> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    long countByRole(String role);

    List<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByIsActive(Boolean isActive);

    long countByIsBlocked(Boolean isBlocked);
}
