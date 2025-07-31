package com.delivery.tracking_service.dto.request;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

@Getter
@Setter
public class UpdateLocationRequest {
    
    @NotNull(message = "Latitude không được để trống")
    @DecimalMin(value = "-90.0", message = "Latitude phải từ -90 đến 90")
    @DecimalMax(value = "90.0", message = "Latitude phải từ -90 đến 90")
    private Double latitude;
    
    @NotNull(message = "Longitude không được để trống")
    @DecimalMin(value = "-180.0", message = "Longitude phải từ -180 đến 180")
    @DecimalMax(value = "180.0", message = "Longitude phải từ -180 đến 180")
    private Double longitude;
    
    private Double accuracy;
    private Double speed;
    private Double heading;
    private Boolean isOnline = true;
}
