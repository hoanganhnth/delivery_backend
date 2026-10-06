package com.delivery.flashsale_service.dto;

import lombok.Data;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Data
public class FlashSaleCampaignDto {
    private Long id;
    private String name;
    private Boolean isRecurring;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private Long adminId;
}
