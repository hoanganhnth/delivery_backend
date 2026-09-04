package com.delivery.restaurant_service.dto.request;

import java.time.LocalTime;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public class UpdateRestaurantRequest {
    @Size(max = 255)
    @Pattern(regexp = ".*\\S.*", message = "name must contain a non-whitespace character")
    private String name;
    @Size(min = 10, max = 2000, message = "address must be between 10 and 2000 characters")
    @Pattern(regexp = ".*\\S.*", message = "address must contain a non-whitespace character")
    private String address;
    @Size(max = 20)
    private String phone;
    private LocalTime openingHour;
    private LocalTime closingHour;
    @Min(1)
    @Max(240)
    private Integer defaultPrepTimeMinutes;
    @Size(max = 4000)
    private String description;
    @DecimalMin("8.0")
    @DecimalMax("24.0")
    private Double addressLat;
    @DecimalMin("102.0")
    @DecimalMax("110.0")
    private Double addressLng;
    private String image;

    // Getters and Setters

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getAddressLat() {
        return addressLat;
    }

    public void setAddressLat(Double addressLat) {
        this.addressLat = addressLat;
    }

    public Double getAddressLng() {
        return addressLng;
    }

    public void setAddressLng(Double addressLng) {
        this.addressLng = addressLng;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
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

    public Integer getDefaultPrepTimeMinutes() {
        return defaultPrepTimeMinutes;
    }

    public void setDefaultPrepTimeMinutes(Integer defaultPrepTimeMinutes) {
        this.defaultPrepTimeMinutes = defaultPrepTimeMinutes;
    }
}
