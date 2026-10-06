package com.delivery.notification.application.api;

import com.delivery.notification.domain.ReplayPayload;

/** Raw serialized data remains part of replay identity. */
public record SendCommand(ReplayPayload payload, String deduplicationKey, Boolean sendPush) {}
