package com.delivery.delivery_service.entity;

public enum DeliveryStatus {
    ASSIGNED("Đã phân công"),
    PICKED_UP("Đã lấy hàng"),
    DELIVERING("Đang giao hàng"),
    DELIVERED("Đã giao thành công"),
    CANCELLED("Đã hủy");

    private final String description;

    DeliveryStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
