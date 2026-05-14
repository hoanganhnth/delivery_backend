package com.delivery.flashsale_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalTime;

@Data
public class CreateCampaignRequest {
    @NotBlank
    private String name;
    
    @NotNull
    private Boolean isRecurring;
    
    @NotNull
    private LocalTime startTime;
    
    @NotNull
    private LocalTime endTime;
}
