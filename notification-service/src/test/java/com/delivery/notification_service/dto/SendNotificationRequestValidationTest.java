package com.delivery.notification_service.dto;

import com.delivery.notification_service.dto.request.SendNotificationRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SendNotificationRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsMissingIdentityContentAndInvalidPriority() {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setPriority("URGENT");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("userId", "title", "message", "type", "priority");
    }
}
