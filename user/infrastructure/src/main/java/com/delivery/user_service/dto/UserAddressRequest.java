package com.delivery.user_service.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddressRequest {
    @NotBlank(message = "label is required")
    @Size(max = 255, message = "label must not exceed 255 characters")
    private String label;

    @NotBlank(message = "recipientName is required")
    @Size(max = 255, message = "recipientName must not exceed 255 characters")
    private String recipientName;

    @NotBlank(message = "phoneNumber is required")
    @Size(max = 255, message = "phoneNumber must not exceed 255 characters")
    private String phoneNumber;

    @NotBlank(message = "addressLine is required")
    @Size(max = 255, message = "addressLine must not exceed 255 characters")
    private String addressLine;

    @NotBlank(message = "ward is required")
    @Size(max = 255, message = "ward must not exceed 255 characters")
    private String ward;

    @NotBlank(message = "district is required")
    @Size(max = 255, message = "district must not exceed 255 characters")
    private String district;

    @NotBlank(message = "city is required")
    @Size(max = 255, message = "city must not exceed 255 characters")
    private String city;

    @Size(max = 255, message = "postalCode must not exceed 255 characters")
    private String postalCode;

    @DecimalMin(value = "-90.0", message = "latitude must be at least -90")
    @DecimalMax(value = "90.0", message = "latitude must be at most 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "longitude must be at least -180")
    @DecimalMax(value = "180.0", message = "longitude must be at most 180")
    private Double longitude;
    private Boolean isDefault;
}
