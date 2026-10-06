package com.delivery.notification.application.api;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/** All operations execute within the adapter-owned row-lock transaction. */
public interface DeliveryPort {
    Optional<StoredNotification<Void>> lock(Long id);
    void push(Long userId, String title, String message, Map<String, String> data);
    void saveSent(Long id, LocalDateTime sentAt);
}
