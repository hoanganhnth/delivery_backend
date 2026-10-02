package com.delivery.restaurant_service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateServiceabilityZoneRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    /** GeoJSON Polygon with one closed outer ring in v1. */
    @NotBlank
    @Size(max = 200_000)
    private String polygonGeoJson;

    @Min(-10_000)
    @Max(10_000)
    private Integer priority = 0;

    private Boolean active = true;
}
