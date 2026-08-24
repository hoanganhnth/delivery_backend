package com.delivery.notification_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Auth-principal-owned notification policy. There is deliberately no
 * transactional opt-out column: transactional lifecycle and safety notices
 * remain deliverable regardless of user marketing preference.
 */
@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference {

    @Id
    @Column(name = "principal_id", nullable = false, updatable = false)
    private Long principalId;

    @Column(name = "marketing_notifications_enabled", nullable = false)
    private boolean marketingNotificationsEnabled;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
