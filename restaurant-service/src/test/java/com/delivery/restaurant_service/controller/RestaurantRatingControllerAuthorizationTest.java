package com.delivery.restaurant_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.restaurant.application.api.RestaurantRatingResult;
import com.delivery.restaurant.application.api.RestaurantRatingUseCase;
import com.delivery.restaurant.application.api.SubmitRestaurantRatingCommand;
import com.delivery.restaurant_service.dto.request.RestaurantRatingRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

class RestaurantRatingControllerAuthorizationTest {

    private final RestaurantRatingUseCase service = mock(RestaurantRatingUseCase.class);
    private final RestaurantRatingController controller = new RestaurantRatingController(service);

    @Test
    void ratingListRejectsMissingAdminRole() {
        AuthenticatedActor userActor = new AuthenticatedActor(21L, "user@example.com", Set.of("USER"));
        var response = controller.getAllRatings(userActor);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(service, never()).getAllRatings();
    }

    @Test
    void ratingModerationRejectsMissingAdminRole() {
        AuthenticatedActor shopActor = new AuthenticatedActor(21L, "shop@example.com", Set.of("SHOP_OWNER"));
        var response = controller.updateRatingStatus(7L, "APPROVED", shopActor);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(service, never()).updateRatingStatus(7L, "APPROVED");
    }

    @Test
    void ratingUsesGatewayCustomerIdentityForDeliveredOrderSubmission() {
        RestaurantRatingRequest request = new RestaurantRatingRequest();
        request.setOrderId(101L);
        request.setRating(5);
        AuthenticatedActor userActor = new AuthenticatedActor(21L, "user@example.com", Set.of("USER"));
        SubmitRestaurantRatingCommand command = new SubmitRestaurantRatingCommand(7L, 21L, 101L, 5, null);
        when(service.submitRating(command)).thenReturn(
                new RestaurantRatingResult(1L, 7L, 21L, 101L, 5, null, "PENDING", null));

        controller.submitRating(7L, userActor, request);

        verify(service).submitRating(command);
    }

    @Test
    void customerRatingEndpointsRejectNonUserRoles() {
        RestaurantRatingRequest request = new RestaurantRatingRequest();
        request.setOrderId(101L);
        request.setRating(5);
        AuthenticatedActor shipperActor = new AuthenticatedActor(21L, "shipper@example.com", Set.of("SHIPPER"));
        AuthenticatedActor shopActor = new AuthenticatedActor(21L, "shop@example.com", Set.of("SHOP_OWNER"));

        assertThatThrownBy(() -> controller.submitRating(7L, shipperActor, request))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.getMyRatings(shopActor))
                .isInstanceOf(AccessDeniedException.class);

        verify(service, never()).getMyRatings(21L);
    }
}
