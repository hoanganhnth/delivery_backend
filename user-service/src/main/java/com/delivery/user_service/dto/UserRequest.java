package com.delivery.user_service.dto;

import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRequest {
    private Long authId;
    private String fullName;
    private String phone;
    private LocalDate dob;
    private String avatarUrl;
    private String address;
}
