package com.delivery.tracking_service.dto.event;

import com.delivery.tracking_service.dto.response.ShipperLocationResponse;

public record LocationFanoutEnvelope(Long deliveryId, ShipperLocationResponse location) {}
