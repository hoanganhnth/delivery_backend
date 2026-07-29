package com.delivery.restaurant_service.dto;

import com.delivery.restaurant_service.dto.request.CreateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogMutationValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void restaurantCreateRejectsBlankNameAndOutOfCountryCoordinates() {
        CreateRestaurantRequest request = new CreateRestaurantRequest();
        request.setName(" ");
        request.setAddressLat(40.0);
        request.setAddressLng(80.0);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name", "addressLat", "addressLng");
    }

    @Test
    void restaurantCreateRequiresCanonicalPickupCoordinates() {
        CreateRestaurantRequest request = new CreateRestaurantRequest();
        request.setName("Restaurant");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("addressLat", "addressLng");
    }

    @Test
    void menuCreateRequiresRestaurantNameAndPositiveBoundedPrice() {
        CreateMenuItemRequest request = new CreateMenuItemRequest();
        request.setRestaurantId(0L);
        request.setName(" ");
        request.setPrice(BigDecimal.ZERO);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("restaurantId", "name", "price");
    }

    @Test
    void restaurantPartialUpdateRejectsBlankNameAndInvalidBounds() {
        UpdateRestaurantRequest request = new UpdateRestaurantRequest();
        request.setName(" ");
        request.setAddress("x".repeat(2001));
        request.setAddressLat(25.0);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name", "address", "addressLat");
    }

    @Test
    void menuPartialUpdateRejectsBlankNameAndInvalidPrice() {
        UpdateMenuItemRequest request = new UpdateMenuItemRequest();
        request.setName(" ");
        request.setRestaurantId(0L);
        request.setPrice(BigDecimal.ZERO);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name", "restaurantId", "price");
    }
}
