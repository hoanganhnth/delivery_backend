package com.delivery.user_service.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddressResponse {
    private Long id;
    private Long userId;
    private String label;
    private String recipientName;
    private String phoneNumber;
    private String addressLine;
    private String ward;
    private String district;
    private String city;
    private String postalCode;
    private Double latitude;
    private Double longitude;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
