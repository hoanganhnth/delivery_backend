package com.delivery.notification_service.dto.request;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * ✅ Send Notification Request DTO theo Backend Instructions
 */
@Getter
@Setter
public class SendNotificationRequest {

    @NotNull
    @Positive
    private Long userId;
    /** Stable Auth principal when the producer has migrated; userId remains legacy routing compatibility. */
    @Positive
    private Long userPrincipalId;
    @NotBlank
    @Size(max = 255)
    private String title;
    @NotBlank
    @Size(max = 2000)
    private String message;
    @NotBlank
    @Size(max = 100)
    private String type;
    @NotNull
    @Pattern(regexp = "HIGH|MEDIUM|LOW")
    private String priority = "MEDIUM";
    private Long relatedEntityId;
    @Size(max = 100)
    private String relatedEntityType;
    @Size(max = 10000)
    private String data; // JSON string for additional data
    @Size(max = 200)
    private String deduplicationKey;
    @NotNull
    private Boolean sendPush = true;
}
