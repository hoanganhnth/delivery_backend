package com.delivery.api_gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserRequest {
    private Long authId;
    private String email;
    private String role;
    private String fullName;
    private String phone;
    private String dob;
    private String address;
}
