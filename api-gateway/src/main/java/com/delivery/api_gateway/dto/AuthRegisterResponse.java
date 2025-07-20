package com.delivery.api_gateway.dto;

import lombok.Data;

@Data
public class AuthRegisterResponse {
    private Long authId;
    private String email;
    private String role;
}
