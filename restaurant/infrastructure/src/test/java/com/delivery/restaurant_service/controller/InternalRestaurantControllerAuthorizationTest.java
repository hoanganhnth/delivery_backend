package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant.application.DefaultRestaurantOwnershipLookupUseCase;
import com.delivery.restaurant.application.DefaultRestaurantManagementAccessUseCase;
import com.delivery.restaurant.application.api.RestaurantTransactionPort;
import com.delivery.restaurant_service.service.JpaRestaurantOwnershipReadAdapter;
import com.delivery.restaurant_service.entity.Restaurant;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

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
        controller = new InternalRestaurantController(ownership(), new SimpleMeterRegistry());
        ReflectionTestUtils.setField(controller, "internalSecret", "test-secret");
    }

    @Test
    void missingOrWrongCredentialIsRejectedBeforeRepositoryAccess() {
        assertEquals(HttpStatus.FORBIDDEN,
                controller.isOwnedBy(7L, 11L, null, null).getStatusCode());
        assertEquals(0, controller.isOwnedBy(7L, 11L, null, null).getBody().getStatus());
        assertEquals(HttpStatus.FORBIDDEN,
                controller.isOwnedBy(7L, 11L, null, "wrong").getStatusCode());
        verifyNoInteractions(restaurantRepository);
    }

    @Test
    void matchingCredentialReturnsRepositoryOwnership() {
        when(restaurantRepository.findById(7L)).thenReturn(Optional.of(ownedRestaurant()));

        var response = controller.isOwnedBy(7L, 11L, null, "test-secret");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getStatus());
        assertTrue(response.getBody().getData());
        verify(restaurantRepository).findById(7L);
    }
    private DefaultRestaurantOwnershipLookupUseCase ownership() {
        return new DefaultRestaurantOwnershipLookupUseCase(new JpaRestaurantOwnershipReadAdapter(restaurantRepository), new RestaurantTransactionPort() {
            @Override public <T> T required(Supplier<T> operation) { return operation.get(); }
            @Override public <T> T readOnly(Supplier<T> operation) { return operation.get(); }
            @Override public <T> T repeatableRead(Supplier<T> operation) { return operation.get(); }
        }, new DefaultRestaurantManagementAccessUseCase());
    }
    private Restaurant ownedRestaurant() { var row = new Restaurant(); row.setCreatorId(11L); return row; }

}
