package com.delivery.restaurant_service.dto.response;

import java.time.LocalTime;

public class RestaurantResponse {
    private Long id;
    private String name;
    private String address;
    private String phone;
    private LocalTime openingHour;
    private LocalTime closingHour;
    private boolean open;
    private String image;

    // Getters and Setters
    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }
    public void setAddress(String address) {
        this.address = address;
    }
    public String getPhone() {
        return phone;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }
    public LocalTime getOpeningHour() {
        return openingHour;
    }
    public void setOpeningHour(LocalTime openingHour) {
        this.openingHour = openingHour;
    }
    public LocalTime getClosingHour() {
        return closingHour;
    }
    public void setClosingHour(LocalTime closingHour) {
        this.closingHour = closingHour;
    }
    public String getImage() {
        return image;
    }
    public void setImage(String image) {
        this.image = image;
    }
}
