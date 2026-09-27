package com.delivery.routing.contracts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Internal request for a customer-facing, bounded ETA window. */
@JsonPropertyOrder({"origin", "destination", "prepMinutes"})
public record EtaWindowRequest(
        @JsonProperty("origin") Coordinate origin,
        @JsonProperty("destination") Coordinate destination,
        @JsonProperty("prepMinutes") Integer prepMinutes) {
}
