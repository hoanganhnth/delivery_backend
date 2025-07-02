package com.delivery.user_service.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Long id;
    private Long authId;
    private String fullName;
    private String phone;
    private LocalDate dob;
    private String avatarUrl;
    private String address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
