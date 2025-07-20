package com.delivery.api_gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterRequest {
    private Long authId;
    private String email;
    private String role;

    private String fullName;
    private String phone;
    private String dob;
    private String address;
}
