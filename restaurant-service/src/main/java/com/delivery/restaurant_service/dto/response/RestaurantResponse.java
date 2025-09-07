package com.delivery.restaurant_service.dto.response;

import java.time.LocalTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestaurantResponse {
    private Long id;
    private String name;
    private String address;
    private String phone;
    private LocalTime openingHour;
    private LocalTime closingHour;
    private boolean open;
    private String image;
    private String description;

}
