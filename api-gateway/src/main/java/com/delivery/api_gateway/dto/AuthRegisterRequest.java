package com.delivery.api_gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthRegisterRequest {
    private String email;
    private String password;
    private String role;
}
