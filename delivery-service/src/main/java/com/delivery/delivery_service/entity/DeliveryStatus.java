package com.delivery.delivery_service.entity;

public enum DeliveryStatus {
    PENDING("Chờ phân công shipper"),
    FINDING_SHIPPER("Đang tìm shipper"),
    WAIT_SHIPPER_CONFIRM("Chờ shipper nhận đơn"),
    SHIPPER_NOT_FOUND("Không tìm được shipper"),
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
