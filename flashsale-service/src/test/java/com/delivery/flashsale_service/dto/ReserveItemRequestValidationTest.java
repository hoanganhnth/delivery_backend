package com.delivery.flashsale_service.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import com.delivery.flashsale_service.dto.CreateCampaignRequest;

class ReserveItemRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void reserveItemRequiresPositiveIdentityQuantityAndPrice() {
        ReserveItemRequest request = new ReserveItemRequest();
        request.setFlashSaleItemId(0L);
        request.setQuantity(-1);
        request.setPrice(BigDecimal.ZERO);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("flashSaleItemId", "quantity", "price");
    }

    @Test
    void campaignNameIsRequiredAndBounded() {
        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setName("x".repeat(256));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name", "isRecurring", "startTime", "endTime");
    }
}
