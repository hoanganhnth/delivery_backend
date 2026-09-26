package com.delivery.restaurant_service.dto.request;


import java.time.LocalTime;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Getter
@Setter
public class CreateRestaurantRequest {
    @Positive
    private Long ownerPrincipalId;
    @NotBlank
    @Size(max = 255)
    private String name;
    @NotBlank(message = "Address must not be blank")
    @Size(min = 10, max = 2000, message = "Address must be between 10 and 2000 characters")
    private String address;
    @Size(max = 20)
    private String phone;
    private LocalTime openingHour;
    private LocalTime closingHour;
    @Min(1)
    @Max(240)
    private Integer defaultPrepTimeMinutes = 30;
    private String image;
    @NotNull
    @DecimalMin("8.0")
    @DecimalMax("24.0")
    private Double addressLat;
    @NotNull
    @DecimalMin("102.0")
    @DecimalMax("110.0")
    private Double addressLng; 
    @Size(max = 4000)
    private String description;

    @AssertTrue(message = "Opening and closing times must both be present or both be absent")
    @JsonIgnore
    public boolean isOperatingHoursPairValid() {
        return (openingHour == null) == (closingHour == null);
    }

}
