package com.delivery.user_service.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void addressRequiresCheckoutFieldsAndValidCoordinates() {
        UserAddressRequest request = UserAddressRequest.builder()
                .label(" ")
                .recipientName("")
                .phoneNumber(" ")
                .addressLine("")
                .ward(" ")
                .district("")
                .city(" ")
                .latitude(91D)
                .longitude(-181D)
                .build();

        var fields = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(fields).contains(
                "label", "recipientName", "phoneNumber", "addressLine",
                "ward", "district", "city", "latitude", "longitude");
    }

    @Test
    void validAddressAcceptsOptionalPostalCodeAndCoordinates() {
        UserAddressRequest request = UserAddressRequest.builder()
                .label("Nhà")
                .recipientName("Nguyễn Văn A")
                .phoneNumber("0900000000")
                .addressLine("1 Đường A")
                .ward("Phường 1")
                .district("Quận 1")
                .city("Hồ Chí Minh")
                .build();

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void profileRejectsInvalidIdentityMetadataAndFutureDob() {
        UserRequest request = UserRequest.builder()
                .authId(0L)
                .email("not-an-email")
                .dob(LocalDate.now().plusDays(1))
                .build();

        var fields = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder("authId", "email", "dob");
    }
}
