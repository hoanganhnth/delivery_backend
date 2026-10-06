package com.delivery.promotion_service.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromotionRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void calculateRequiresPositiveShopAndNonNegativeAmounts() {
        CartContextRequest invalid = CartContextRequest.builder()
                .shopId(0L)
                .subTotal(new BigDecimal("-1"))
                .shippingFee(null)
                .build();
        CartContextRequest valid = CartContextRequest.builder()
                .shopId(7L)
                .subTotal(BigDecimal.ZERO)
                .shippingFee(new BigDecimal("15000"))
                .build();

        assertFalse(validator.validate(invalid).isEmpty());
        assertTrue(validator.validate(valid).isEmpty());
    }

    @Test
    void reserveRequiresStableIdentityAndCanonicalAmounts() {
        ReserveRequest invalid = ReserveRequest.builder()
                .userId(0L)
                .orderId(null)
                .voucherId(-1L)
                .build();
        ReserveRequest valid = ReserveRequest.builder()
                .reservationId(UUID.randomUUID())
                .userId(3L)
                .orderId(9L)
                .voucherId(11L)
                .restaurantId(12L)
                .subtotal(new BigDecimal("100000"))
                .shippingFee(new BigDecimal("15000"))
                .build();

        assertFalse(validator.validate(invalid).isEmpty());
        assertTrue(validator.validate(valid).isEmpty());
    }
}
