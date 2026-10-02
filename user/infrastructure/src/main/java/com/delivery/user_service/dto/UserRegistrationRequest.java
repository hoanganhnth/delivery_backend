package com.delivery.user_service.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegistrationRequest {
    @NotBlank
    private String provisioningToken;

    @Size(max = 255)
    private String fullName;

    @Size(max = 255)
    private String phone;

    @PastOrPresent
    private LocalDate dob;

    @Size(max = 255)
    private String avatarUrl;

    @Size(max = 255)
    private String address;
}
