package com.delivery.routing.contracts;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.List;

@JsonPropertyOrder({"profile", "origin", "destinations", "departureAt"})
public record MatrixRequest(
        @JsonProperty("profile") String profile,
        @JsonProperty("origin") Coordinate origin,
        @JsonProperty("destinations") List<Destination> destinations,
        @JsonProperty("departureAt") @JsonFormat(shape = JsonFormat.Shape.STRING) Instant departureAt) {

    @JsonPropertyOrder({"id", "coordinate"})
    public record Destination(
            @JsonProperty("id") String id,
            @JsonProperty("coordinate") Coordinate coordinate) {
    }
}
