package com.delivery.livestream_service.dto.response;

import com.delivery.livestream_service.enums.LivestreamModerationAction;
import java.time.Instant;
import java.util.UUID;

public record LivestreamModerationResponse(Long auditId, UUID livestreamId,
        LivestreamModerationAction action, Long productId,
        @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        Instant appliedAt) {
}
