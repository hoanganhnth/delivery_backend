package com.delivery.restaurant_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Internal decision contract consumed by Order; never exposes raw polygons. */
@Getter
@AllArgsConstructor
public class ServiceabilityDecisionResponse {
    private boolean enabled;
    private boolean serviceable;
    private Long zoneId;
    private Long zoneRevision;
    private String reason;
}
