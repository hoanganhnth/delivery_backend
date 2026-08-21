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

    Optional<User> findByPrincipalId(Long principalId);

    Optional<User> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    long countByRole(String role);

    List<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByIsActive(Boolean isActive);

    long countByIsBlocked(Boolean isBlocked);

    @Query("""
            select user from User user
            where user.principalId is not null
              and not exists (
                select event.id from IdentityOutboxEvent event
                where event.eventType = :eventType and event.aggregateId = user.principalId)
            order by user.id asc
            """)
    List<User> findWithoutIdentityProfileEvent(@Param("eventType") String eventType, Pageable pageable);

    @Query("""
            select count(user) from User user
            where user.principalId is not null
              and not exists (
                select event.id from IdentityOutboxEvent event
                where event.eventType = :eventType and event.aggregateId = user.principalId)
            """)
    long countWithoutIdentityProfileEvent(@Param("eventType") String eventType);
}
