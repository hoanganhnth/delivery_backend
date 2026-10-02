package com.delivery.restaurant_service.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import com.delivery.restaurant.domain.ownership.OwnerAssignmentException;
import com.delivery.restaurant.domain.ownership.OwnerAssignmentFailure;
import com.delivery.restaurant.domain.rating.RestaurantRatingConflictException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgumentIsReportedAsBadRequest() {
        var response = handler.handleIllegalArgument(
                new IllegalArgumentException("Order is not pending for this restaurant"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isZero();
        assertThat(response.getBody().getMessage())
                .isEqualTo("Order is not pending for this restaurant");
    }

    @Test
    void ratingConflictIsReportedAsConflict() {
        var response = handler.handleRatingConflict(
                new RestaurantRatingConflictException("Order has already been rated for this restaurant"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isZero();
        assertThat(response.getBody().getMessage())
                .isEqualTo("Order has already been rated for this restaurant");
    }

    @Test
    void ownerAssignmentSeparatesAuthorizationFromInvalidTarget() {
        var forbidden = handler.handleOwnerAssignment(
                new OwnerAssignmentException(OwnerAssignmentFailure.CANNOT_ASSIGN_ANOTHER_OWNER));
        var invalidTarget = handler.handleOwnerAssignment(
                new OwnerAssignmentException(OwnerAssignmentFailure.OWNER_NOT_ACTIVE));

        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(invalidTarget.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidTarget.getBody()).isNotNull();
        assertThat(invalidTarget.getBody().getMessage()).isEqualTo("INVALID_OWNER_PRINCIPAL");
    }
}
