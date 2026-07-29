package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.service.RestaurantOrderEventPublisher;
import com.delivery.restaurant_service.dto.request.ConfirmRestaurantOrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantOrderControllerAuthorizationTest {

    @Mock
    private RestaurantOrderEventPublisher eventPublisher;

    @Mock
    private RestaurantRepository restaurantRepository;

    private RestaurantOrderController controller;

    @BeforeEach
    void setUp() {
        controller = new RestaurantOrderController(eventPublisher, restaurantRepository);
    }

    @Test
    void ownerCanConfirmOnlyAnOwnedRestaurant() {
        when(restaurantRepository.existsByIdAndCreatorId(7L, 11L)).thenReturn(true);

        var response = controller.confirmOrder(
                101L,
                confirmRequest(7L, 20),
                11L,
                RoleConstants.OWNER);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(eventPublisher).publishConfirmed(101L, 7L, 11L, 20, null);
    }

    @Test
    void ownerCannotConfirmAnotherRestaurant() {
        when(restaurantRepository.existsByIdAndCreatorId(7L, 11L)).thenReturn(false);

        var response = controller.confirmOrder(
                101L,
                confirmRequest(7L, 20),
                11L,
                RoleConstants.OWNER);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(eventPublisher, never()).publishConfirmed(101L, 7L, 11L, 20, null);
    }

    @Test
    void invalidPreparationTimeDoesNotPublish() {
        when(restaurantRepository.existsByIdAndCreatorId(7L, 11L)).thenReturn(true);

        var response = controller.confirmOrder(
                101L,
                confirmRequest(7L, 0),
                11L,
                RoleConstants.OWNER);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(eventPublisher, never()).publishConfirmed(101L, 7L, 11L, 0, null);
    }

    private ConfirmRestaurantOrderRequest confirmRequest(Long restaurantId, Integer prepTime) {
        ConfirmRestaurantOrderRequest request = new ConfirmRestaurantOrderRequest();
        request.setRestaurantId(restaurantId);
        request.setEstimatedPrepTime(prepTime);
        return request;
    }
}
