package com.delivery.routing.contracts;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;

@JsonPropertyOrder({"profile", "origin", "destination", "departureAt", "includeGeometry"})
public record RouteRequest(
        @JsonProperty("profile") String profile,
        @JsonProperty("origin") Coordinate origin,
        @JsonProperty("destination") Coordinate destination,
        @JsonProperty("departureAt") @JsonFormat(shape = JsonFormat.Shape.STRING) Instant departureAt,
        @JsonProperty("includeGeometry") boolean includeGeometry) {
}
