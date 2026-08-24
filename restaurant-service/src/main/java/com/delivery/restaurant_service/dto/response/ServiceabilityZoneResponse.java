package com.delivery.restaurant_service.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ServiceabilityZoneResponse {
    private Long id;
    private Long restaurantId;
    private String name;
    private String polygonGeoJson;
    private Integer priority;
    private boolean active;
    private Long revision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
