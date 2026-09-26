package com.delivery.restaurant_service.dto;

import com.delivery.restaurant_service.dto.request.CreateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;

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
    void restaurantCreateRejectsEitherHalfOfOperatingHoursPair() {
        CreateRestaurantRequest request = validRestaurantRequest();
        request.setOpeningHour(LocalTime.of(18, 0));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("operatingHoursPairValid");

        request.setOpeningHour(null);
        request.setClosingHour(LocalTime.of(2, 0));
        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("operatingHoursPairValid");
    }

    @Test
    void restaurantCreateAllowsAlwaysOpenOrCompleteOperatingHoursPair() {
        CreateRestaurantRequest request = validRestaurantRequest();
        assertThat(validator.validate(request)).isEmpty();

        request.setOpeningHour(LocalTime.of(18, 0));
        request.setClosingHour(LocalTime.of(2, 0));
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void restaurantCreateRejectsNonPositiveRequestedOwner() {
        CreateRestaurantRequest request = new CreateRestaurantRequest();
        request.setName("Restaurant");
        request.setAddress("123 Valid Street");
        request.setAddressLat(10.78);
        request.setAddressLng(106.69);
        request.setOwnerPrincipalId(0L);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("ownerPrincipalId");
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
    void restaurantPartialUpdateRejectsWhitespaceOnlyAddress() {
        UpdateRestaurantRequest request = new UpdateRestaurantRequest();
        request.setAddress(" ".repeat(10));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("address");
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

    private CreateRestaurantRequest validRestaurantRequest() {
        CreateRestaurantRequest request = new CreateRestaurantRequest();
        request.setName("Valid Restaurant");
        request.setAddress("123 Valid Street");
        request.setAddressLat(10.78);
        request.setAddressLng(106.69);
        return request;
    }
}
