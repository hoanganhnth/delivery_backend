package com.delivery.routing.contracts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"durationSeconds", "distanceMeters", "geometry", "source"})
public record RouteResponse(
        @JsonProperty("durationSeconds") long durationSeconds,
        @JsonProperty("distanceMeters") long distanceMeters,
        @JsonProperty("geometry") String geometry,
        @JsonProperty("source") String source) {
}
