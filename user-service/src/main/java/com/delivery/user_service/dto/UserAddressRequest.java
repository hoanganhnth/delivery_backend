package com.delivery.user_service.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddressRequest {
    private String label;
    private String addressLine;
    private String ward;
    private String district;
    private String city;
    private String postalCode;
    private Double latitude;
    private Double longitude;
    private Boolean isDefault;
}
