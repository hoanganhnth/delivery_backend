package com.delivery.restaurant_service.dto.request;


import java.time.LocalTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRestaurantRequest {
    private String name;
    private String address;
    private String phone;
    private LocalTime openingHour;
    private LocalTime closingHour;
    private String image;
    private Double addressLat;
    private Double addressLng; 
    private String description;

}