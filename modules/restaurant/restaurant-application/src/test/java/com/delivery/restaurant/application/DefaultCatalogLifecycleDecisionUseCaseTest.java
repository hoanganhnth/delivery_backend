package com.delivery.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.restaurant.domain.catalog.CatalogActorRole;
import com.delivery.restaurant.domain.catalog.CatalogDomainException;
import com.delivery.restaurant.domain.catalog.MenuItemLifecyclePolicy;
import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant.domain.catalog.RestaurantLifecyclePolicy;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import org.junit.jupiter.api.Test;

class DefaultCatalogLifecycleDecisionUseCaseTest {

    private final DefaultCatalogLifecycleDecisionUseCase useCase =
            new DefaultCatalogLifecycleDecisionUseCase(
                    new RestaurantLifecyclePolicy(), new MenuItemLifecyclePolicy());

    @Test
    void delegatesRestaurantTransitionAndPreservesIdempotency() {
        assertThat(useCase.decideRestaurant(
                RestaurantStatus.ACTIVE, RestaurantStatus.PAUSED, CatalogActorRole.SHOP_OWNER))
                .isEqualTo(RestaurantStatus.PAUSED);
        assertThat(useCase.decideRestaurant(
                RestaurantStatus.PAUSED, RestaurantStatus.PAUSED, CatalogActorRole.SHOP_OWNER))
                .isEqualTo(RestaurantStatus.PAUSED);
    }

    @Test
    void delegatesMenuRestoreAuthorization() {
        assertThat(useCase.decideMenuItem(
                MenuItemStatus.ARCHIVED, MenuItemStatus.DISCONTINUED, CatalogActorRole.ADMIN))
                .isEqualTo(MenuItemStatus.DISCONTINUED);
        assertThatThrownBy(() -> useCase.decideMenuItem(
                MenuItemStatus.ARCHIVED, MenuItemStatus.DISCONTINUED, CatalogActorRole.SHOP_OWNER))
                .isInstanceOf(CatalogDomainException.class);
    }
}
