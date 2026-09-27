package com.delivery.routing.contracts;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.List;

@JsonPropertyOrder({"results", "generatedAt"})
public record MatrixResponse(
        @JsonProperty("results") List<Result> results,
        @JsonProperty("generatedAt") @JsonFormat(shape = JsonFormat.Shape.STRING) Instant generatedAt) {

    @JsonPropertyOrder({"id", "durationSeconds", "distanceMeters", "source"})
    public record Result(
            @JsonProperty("id") String id,
            @JsonProperty("durationSeconds") long durationSeconds,
            @JsonProperty("distanceMeters") long distanceMeters,
            @JsonProperty("source") String source) {
    }
}
