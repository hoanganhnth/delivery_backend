package com.delivery.restaurant_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.delivery.restaurant_service.service.RestaurantRatingService;
import com.delivery.restaurant_service.client.OrderEligibilityClient;
import com.delivery.restaurant_service.dto.request.RestaurantRatingRequest;
import org.springframework.security.access.AccessDeniedException;

class RestaurantRatingControllerAuthorizationTest {

    private final RestaurantRatingService service = mock(RestaurantRatingService.class);
    private final OrderEligibilityClient eligibilityClient = mock(OrderEligibilityClient.class);
    private final RestaurantRatingController controller = new RestaurantRatingController(service, eligibilityClient);

    @Test
    void ratingListRejectsMissingAdminRole() {
        var response = controller.getAllRatings("USER");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(service, never()).getAllRatings();
    }

    @Test
    void ratingModerationRejectsMissingAdminRole() {
        var response = controller.updateRatingStatus(7L, "APPROVED", "SHOP_OWNER");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(service, never()).updateRatingStatus(7L, "APPROVED");
    }

    @Test
    void ratingUsesGatewayCustomerIdentityForDeliveredOrderCheck() {
        RestaurantRatingRequest request = new RestaurantRatingRequest();
        request.setOrderId(101L);
        request.setRating(5);

        controller.submitRating(7L, 21L, "USER", request);

        verify(eligibilityClient).requireDeliveredOrder(101L, 21L, 7L);
        verify(service).submitRating(7L, 21L, request);
    }

    @Test
    void customerRatingEndpointsRejectNonUserRoles() {
        RestaurantRatingRequest request = new RestaurantRatingRequest();
        request.setOrderId(101L);
        request.setRating(5);

        assertThatThrownBy(() -> controller.submitRating(7L, 21L, "SHIPPER", request))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.getMyRatings(21L, "SHOP_OWNER"))
                .isInstanceOf(AccessDeniedException.class);

        verify(eligibilityClient, never()).requireDeliveredOrder(101L, 21L, 7L);
        verify(service, never()).getMyRatings(21L);
    }
}
