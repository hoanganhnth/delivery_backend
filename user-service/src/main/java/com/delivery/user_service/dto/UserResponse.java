package com.delivery.user_service.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
public class UserResponse {
    private Long id;
    private Long authId;
    private String email;
    private String role;

    private String fullName;
    private String phone;
    private LocalDate dob;
    private String avatarUrl;
    private String address;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
