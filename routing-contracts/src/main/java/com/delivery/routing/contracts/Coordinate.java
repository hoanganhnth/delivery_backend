package com.delivery.routing.contracts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** A latitude/longitude pair used by the routing wire contract. */
@JsonPropertyOrder({"lat", "lng"})
public record Coordinate(
        @JsonProperty("lat") double lat,
        @JsonProperty("lng") double lng) {

    public Coordinate {
        if (!Double.isFinite(lat) || !Double.isFinite(lng)
                || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Invalid coordinate");
        }
    }
}
