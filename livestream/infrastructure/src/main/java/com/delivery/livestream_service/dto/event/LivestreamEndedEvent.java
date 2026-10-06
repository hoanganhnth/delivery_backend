package com.delivery.livestream_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LivestreamEndedEvent {

    private UUID livestreamId;
    private Long sellerId;
    private Long restaurantId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long durationMinutes;
}
