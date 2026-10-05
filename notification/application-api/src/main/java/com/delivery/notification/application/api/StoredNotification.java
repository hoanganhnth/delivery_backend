package com.delivery.notification.application.api;

import com.delivery.notification.domain.ReplayPayload;

/** R is an opaque adapter-owned response; application rules use only the immutable view. */
public record StoredNotification<R>(Long id, ReplayPayload payload, String status, R response) {}
