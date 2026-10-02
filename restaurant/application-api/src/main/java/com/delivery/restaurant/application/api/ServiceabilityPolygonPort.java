package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.serviceability.ServiceabilityGeometry.Polygon;

public interface ServiceabilityPolygonPort {
    Polygon parse(String geoJson);
}
