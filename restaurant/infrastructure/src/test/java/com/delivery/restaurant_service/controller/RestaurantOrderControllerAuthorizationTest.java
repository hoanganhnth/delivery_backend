package com.delivery.restaurant_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.restaurant.application.api.RestaurantOrderDecisionUseCase;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant.application.DefaultRestaurantOwnershipLookupUseCase;
import com.delivery.restaurant.application.DefaultRestaurantManagementAccessUseCase;
import com.delivery.restaurant.application.api.RestaurantTransactionPort;
import com.delivery.restaurant_service.service.JpaRestaurantOwnershipReadAdapter;
import com.delivery.restaurant_service.entity.Restaurant;
import java.util.Optional;
import java.util.function.Supplier;
import com.delivery.restaurant_service.dto.request.ConfirmRestaurantOrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantOrderControllerAuthorizationTest {

    @Mock
    private RestaurantOrderDecisionUseCase eventPublisher;

    @Mock
    private RestaurantRepository restaurantRepository;

    private RestaurantOrderController controller;

    @BeforeEach
    void setUp() {
        controller = new RestaurantOrderController(eventPublisher, ownership());
    }

    @Test
    void ownerCanConfirmOnlyAnOwnedRestaurant() {
        when(restaurantRepository.findById(7L)).thenReturn(Optional.of(ownedRestaurant()));
        AuthenticatedActor actor = new AuthenticatedActor(11L, "owner@example.com", Set.of(RoleConstants.OWNER));

        var response = controller.confirmOrder(
                101L,
                confirmRequest(7L, 20),
                actor);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(eventPublisher).confirm(101L, 7L, 11L, 20, null);
    }

    @Test
    void ownerCannotConfirmAnotherRestaurant() {
        when(restaurantRepository.findById(7L)).thenReturn(Optional.empty());
        AuthenticatedActor actor = new AuthenticatedActor(11L, "owner@example.com", Set.of(RoleConstants.OWNER));

        var response = controller.confirmOrder(
                101L,
                confirmRequest(7L, 20),
                actor);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(eventPublisher, never()).confirm(101L, 7L, 11L, 20, null);
    }

    @Test
    void invalidPreparationTimeDoesNotPublish() {
        when(restaurantRepository.findById(7L)).thenReturn(Optional.of(ownedRestaurant()));
        AuthenticatedActor actor = new AuthenticatedActor(11L, "owner@example.com", Set.of(RoleConstants.OWNER));

        var response = controller.confirmOrder(
                101L,
                confirmRequest(7L, 0),
                actor);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(eventPublisher, never()).confirm(101L, 7L, 11L, 0, null);
    }

    @Test
    void adminCanConfirmAnyRestaurantOrder() {
        AuthenticatedActor actor = new AuthenticatedActor(99L, "admin@example.com", Set.of(RoleConstants.ADMIN));

        var response = controller.confirmOrder(
                101L,
                confirmRequest(7L, 20),
                actor);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(eventPublisher).confirm(101L, 7L, 99L, 20, null);
    }

    @Test
    void assignedPrincipalOwnerCanConfirmWhenCreatorAndLegacyIdDiffer() {
        var restaurant = new Restaurant(); restaurant.setOwnerPrincipalId(71L); restaurant.setCreatorId(99L);
        when(restaurantRepository.findById(7L)).thenReturn(Optional.of(restaurant));
        var actor = new AuthenticatedActor(71L, 171L, "owner@example.com", Set.of(RoleConstants.OWNER));
        assertEquals(HttpStatus.OK, controller.confirmOrder(101L, confirmRequest(7L, 20), actor).getStatusCode());
        verify(eventPublisher).confirm(101L, 7L, 171L, 20, null);
    }

    @Test
    void formerCreatorCannotConfirmOncePrincipalOwnerIsAssigned() {
        var restaurant = new Restaurant(); restaurant.setOwnerPrincipalId(71L); restaurant.setCreatorId(99L);
        when(restaurantRepository.findById(7L)).thenReturn(Optional.of(restaurant));
        var actor = new AuthenticatedActor(99L, 99L, "former@example.com", Set.of(RoleConstants.OWNER));
        assertEquals(HttpStatus.FORBIDDEN, controller.confirmOrder(101L, confirmRequest(7L, 20), actor).getStatusCode());
        verify(eventPublisher, never()).confirm(101L, 7L, 99L, 20, null);
    }

    private ConfirmRestaurantOrderRequest confirmRequest(Long restaurantId, Integer prepTime) {
        ConfirmRestaurantOrderRequest request = new ConfirmRestaurantOrderRequest();
        request.setRestaurantId(restaurantId);
        request.setEstimatedPrepTime(prepTime);
        return request;
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
