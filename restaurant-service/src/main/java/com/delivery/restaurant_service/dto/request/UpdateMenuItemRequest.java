package com.delivery.restaurant_service.dto.request;

import com.delivery.restaurant_service.entity.MenuItem;

import java.math.BigDecimal;

public class UpdateMenuItemRequest {
    private String name;
    private String description;
    private BigDecimal price;
    private MenuItem.Status status; // AVAILABLE, SOLD_OUT, DISCONTINUED
    private String image;
//    private Long restaurantId;

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
