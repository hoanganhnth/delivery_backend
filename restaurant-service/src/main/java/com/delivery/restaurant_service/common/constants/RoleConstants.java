package com.delivery.restaurant_service.common.constants;


import java.util.List;

public class RoleConstants {
    public static final String ADMIN = "ADMIN";
    public static final String OWNER = "SHOP_OWNER";
    public static final String CUSTOMER = "USER";
    public static final String SHIPPER = "SHIPPER";

    public static final List<String> ALLOWED_CREATORS = List.of(ADMIN, OWNER);
}