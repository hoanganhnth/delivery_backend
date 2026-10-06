package com.delivery.notification_service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** The only user-controlled notification preference in the MVP policy. */
@Getter
@Setter
public class UpdateMarketingNotificationPreferenceRequest {

    @NotNull
    private Boolean marketingNotificationsEnabled;
}
