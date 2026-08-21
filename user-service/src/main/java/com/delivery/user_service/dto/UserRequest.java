package com.delivery.user_service.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRequest {
    @Positive(message = "authId must be positive")
    private Long authId;

    @Positive(message = "principalId must be positive")
    private Long principalId;

    @Email(message = "email must be valid")
    @Size(max = 255, message = "email must not exceed 255 characters")
    private String email;

    @Size(max = 255, message = "role must not exceed 255 characters")
    private String role;

    @Size(max = 255, message = "fullName must not exceed 255 characters")
    private String fullName;

    @Size(max = 255, message = "phone must not exceed 255 characters")
    private String phone;

    @PastOrPresent(message = "dob must not be in the future")
    private LocalDate dob;

    @Size(max = 255, message = "avatarUrl must not exceed 255 characters")
    private String avatarUrl;

    @Size(max = 255, message = "address must not exceed 255 characters")
    private String address;
}
