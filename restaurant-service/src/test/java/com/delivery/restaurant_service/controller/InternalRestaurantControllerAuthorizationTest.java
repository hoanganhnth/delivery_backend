package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalRestaurantControllerAuthorizationTest {

    @Mock RestaurantRepository restaurantRepository;

    private InternalRestaurantController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalRestaurantController(restaurantRepository);
        ReflectionTestUtils.setField(controller, "internalSecret", "test-secret");
    }

    @Test
    void missingOrWrongCredentialIsRejectedBeforeRepositoryAccess() {
        assertEquals(HttpStatus.FORBIDDEN,
                controller.isOwnedBy(7L, 11L, null).getStatusCode());
        assertEquals(0, controller.isOwnedBy(7L, 11L, null).getBody().getStatus());
        assertEquals(HttpStatus.FORBIDDEN,
                controller.isOwnedBy(7L, 11L, "wrong").getStatusCode());
        verifyNoInteractions(restaurantRepository);
    }

    @Test
    void matchingCredentialReturnsRepositoryOwnership() {
        when(restaurantRepository.existsByIdAndCreatorId(7L, 11L)).thenReturn(true);

        var response = controller.isOwnedBy(7L, 11L, "test-secret");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getStatus());
        assertTrue(response.getBody().getData());
        verify(restaurantRepository).existsByIdAndCreatorId(7L, 11L);
    }
}
