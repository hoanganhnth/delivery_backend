package com.delivery.restaurant_service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateServiceabilityZoneRequest {

    @NotNull
    @Min(0)
    private Long revision;

    @Size(max = 120)
    private String name;

    @Size(max = 200_000)
    private String polygonGeoJson;

    @Min(-10_000)
    @Max(10_000)
    private Integer priority;

    private Boolean active;
}
