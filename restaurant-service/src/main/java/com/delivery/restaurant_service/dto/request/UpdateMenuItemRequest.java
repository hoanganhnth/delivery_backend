package com.delivery.restaurant_service.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.delivery.restaurant_service.entity.MenuItem;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateMenuItemRequest {
    @Size(max = 255)
    @Pattern(regexp = ".*\\S.*", message = "name must contain a non-whitespace character")
    private String name;
    @Size(max = 4000)
    private String description;
    @DecimalMin("0.01")
    @DecimalMax("10000000")
    private BigDecimal price;
    private MenuItem.Status status; // AVAILABLE, SOLD_OUT, DISCONTINUED
    private String image;
    @Positive
    private Long restaurantId;

    public Long getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(Long restaurantId) {
        this.restaurantId = restaurantId;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public MenuItem.Status getStatus() {
        return status;
    }

    public void setStatus(MenuItem.Status status) {
        this.status = status;
    }
}
