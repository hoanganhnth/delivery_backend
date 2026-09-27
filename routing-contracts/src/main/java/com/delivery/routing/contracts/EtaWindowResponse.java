package com.delivery.routing.contracts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"minMinutes", "maxMinutes", "source"})
public record EtaWindowResponse(
        @JsonProperty("minMinutes") int minMinutes,
        @JsonProperty("maxMinutes") int maxMinutes,
        @JsonProperty("source") String source) {
}
