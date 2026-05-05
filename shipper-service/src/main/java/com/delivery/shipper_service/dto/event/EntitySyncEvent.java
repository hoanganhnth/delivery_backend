package com.delivery.shipper_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntitySyncEvent {
    private String entityType; // "RESTAURANT", "DISH", "SHIPPER"
    private String action;     // "CREATE", "UPDATE", "DELETE"
    private String entityId;
    private Map<String, Object> payload;
}
