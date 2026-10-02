package com.delivery.restaurant_service.dto.response;

import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestaurantResponse {
    private Long id;
    private String name;
    private String address;
    private String phone;
    
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime openingHour;
    
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime closingHour;

    private Integer defaultPrepTimeMinutes;
    
    private boolean open;
    private String image;
    private String description;
    private Double latitude;
    private Double longitude;
    private Double rating;
    private Integer ratingCount;
    private RestaurantStatus lifecycleStatus;
    private Long version;
    private String timeZone;
}
