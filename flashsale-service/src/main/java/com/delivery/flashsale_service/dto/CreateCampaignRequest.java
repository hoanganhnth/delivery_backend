package com.delivery.flashsale_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalTime;

@Data
public class CreateCampaignRequest {
    @NotBlank
    @Size(max = 255)
    private String name;
    
    @NotNull
    private Boolean isRecurring;
    
    @NotNull
    private LocalTime startTime;
    
    @NotNull
    private LocalTime endTime;
}
