package com.delivery.auth_service.dto;

import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsBlankRegistrationFieldsAndInvalidEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("not-an-email");
        request.setPassword(" ");
        request.setRole("");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                .contains("email", "password", "role");
    }

    @Test
    void refreshTokenMustNotBeBlank() {
        RefreshTokenRequest request = new RefreshTokenRequest();

        assertThat(validator.validate(request)).isNotEmpty();
    }
}
