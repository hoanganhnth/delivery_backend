package com.delivery.search_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntitySyncEvent {
    private UUID eventId;
    private LocalDateTime occurredAt;
    private String entityType; // "RESTAURANT", "DISH", "SHIPPER"
    private String action; // "CREATE", "UPDATE", "DELETE"
    private String entityId;
    private Map<String, Object> payload;
}
