package com.delivery.shipper_service.dto;

import com.delivery.shipper_service.dto.request.ShipperRatingRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShipperRatingRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void ratingRequiresOrderAndOneToFiveScoreWithBoundedComment() {
        ShipperRatingRequest invalid = new ShipperRatingRequest();
        invalid.setOrderId(0L);
        invalid.setRating(6);
        invalid.setComment("x".repeat(501));

        ShipperRatingRequest valid = new ShipperRatingRequest();
        valid.setOrderId(9L);
        valid.setRating(5);

        assertThat(validator.validate(invalid)).hasSize(3);
        assertThat(validator.validate(valid)).isEmpty();
    }
}
