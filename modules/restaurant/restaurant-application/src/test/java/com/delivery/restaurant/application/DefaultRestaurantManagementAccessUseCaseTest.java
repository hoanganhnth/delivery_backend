package com.delivery.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.restaurant.application.api.RestaurantManagementAccessUseCase;
import com.delivery.restaurant.domain.ownership.ManagementAccessFailure;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.domain.ownership.RestaurantManagementFacts;
import org.junit.jupiter.api.Test;

class DefaultRestaurantManagementAccessUseCaseTest {

    private final RestaurantManagementAccessUseCase useCase =
            new DefaultRestaurantManagementAccessUseCase();

    @Test
    void adminAndRecordedOwnerHaveDirectManagementAccess() {
        var facts = new RestaurantManagementFacts(10L, 7L);

        assertThat(useCase.resolve(
                facts, 99L, 700L, RestaurantActorRole.ADMIN, true).usedLegacyFallback())
                .isFalse();
        assertThat(useCase.resolve(
                facts, 10L, 700L, RestaurantActorRole.SHOP_OWNER, true).usedLegacyFallback())
                .isFalse();
    }

    @Test
    void unmigratedRestaurantPermitsMatchingLegacyOwnerOnlyBeforeEnforcement() {
        var facts = new RestaurantManagementFacts(null, 700L);

        assertThat(useCase.resolve(
                facts, 10L, 700L, RestaurantActorRole.SHOP_OWNER, false).usedLegacyFallback())
                .isTrue();

        assertFailure(() -> useCase.resolve(
                facts, 10L, 700L, RestaurantActorRole.SHOP_OWNER, true),
                ManagementAccessFailure.ACTOR_DOES_NOT_OWN_RESTAURANT);
        assertFailure(() -> useCase.resolve(
                facts, 10L, 701L, RestaurantActorRole.SHOP_OWNER, false),
                ManagementAccessFailure.ACTOR_DOES_NOT_OWN_RESTAURANT);
    }

    @Test
    void missingPrincipalOrUnsupportedActorFailsClosed() {
        var facts = new RestaurantManagementFacts(10L, 700L);

        assertFailure(() -> useCase.resolve(
                facts, null, 700L, RestaurantActorRole.SHOP_OWNER, false),
                ManagementAccessFailure.PRINCIPAL_REQUIRED);
        assertFailure(() -> useCase.resolve(
                facts, 10L, 700L, RestaurantActorRole.OTHER, false),
                ManagementAccessFailure.ACTOR_NOT_ALLOWED);
        assertThatThrownBy(() -> useCase.resolve(
                null, 10L, 700L, RestaurantActorRole.ADMIN, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("facts are required");
    }

    private void assertFailure(Runnable action, ManagementAccessFailure expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ManagementAccessException.class)
                .satisfies(error -> assertThat(((ManagementAccessException) error).failure())
                        .isEqualTo(expected));
    }
}
