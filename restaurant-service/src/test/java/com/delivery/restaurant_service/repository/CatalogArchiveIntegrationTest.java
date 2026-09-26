package com.delivery.restaurant_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class CatalogArchiveIntegrationTest {

    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MenuItemRepository menuItemRepository;

    @Test
    void archivingRestaurantPreservesMenuRowsAndTheirSellingState() {
        Restaurant restaurant = new Restaurant();
        restaurant.setName("History Restaurant");
        restaurant.setCreatorId(1L);
        restaurant = restaurantRepository.saveAndFlush(restaurant);
        MenuItem item = new MenuItem();
        item.setRestaurant(restaurant);
        item.setName("Historical Dish");
        item.setPrice(BigDecimal.TEN);
        item.setStatus(MenuItem.Status.AVAILABLE);
        item = menuItemRepository.saveAndFlush(item);

        restaurant.setLifecycleStatus(RestaurantStatus.ARCHIVED);
        restaurantRepository.saveAndFlush(restaurant);

        assertThat(restaurantRepository.findById(restaurant.getId()).orElseThrow()
                .getLifecycleStatus()).isEqualTo(RestaurantStatus.ARCHIVED);
        assertThat(menuItemRepository.findById(item.getId()).orElseThrow().getStatus())
                .isEqualTo(MenuItem.Status.AVAILABLE);
        assertThat(menuItemRepository.findPageByRestaurantIdAndStatusAndRestaurantLifecycleStatusNot(
                restaurant.getId(), MenuItem.Status.AVAILABLE, RestaurantStatus.ARCHIVED,
                PageRequest.of(0, 24)).getTotalElements()).isZero();
        assertThat(menuItemRepository.findPageByRestaurantId(restaurant.getId(), PageRequest.of(0, 24))
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    void publicRestaurantProjectionHidesArchivedButManagementCanStillResolveHistory() {
        Restaurant active = new Restaurant();
        active.setName("Active Noodle Shop");
        active.setCreatorId(1L);
        restaurantRepository.saveAndFlush(active);

        Restaurant archived = new Restaurant();
        archived.setName("Archived Noodle Shop");
        archived.setCreatorId(2L);
        archived.setLifecycleStatus(RestaurantStatus.ARCHIVED);
        archived = restaurantRepository.saveAndFlush(archived);

        assertThat(restaurantRepository.findByLifecycleStatusNot(
                RestaurantStatus.ARCHIVED, PageRequest.of(0, 24)).getContent())
                .extracting(Restaurant::getName)
                .containsExactly("Active Noodle Shop");
        assertThat(restaurantRepository.findPageByNameContainingIgnoreCaseAndLifecycleStatusNot(
                "noodle", RestaurantStatus.ARCHIVED, PageRequest.of(0, 24)).getContent())
                .extracting(Restaurant::getName)
                .containsExactly("Active Noodle Shop");
        assertThat(restaurantRepository.findById(archived.getId())).isPresent();
        assertThat(restaurantRepository.findAll(PageRequest.of(0, 24)).getContent())
                .extracting(Restaurant::getLifecycleStatus)
                .contains(RestaurantStatus.ARCHIVED);
    }

    @Test
    void publicMenuProjectionReturnsAvailableOnlyAndHidesArchivedParent() {
        Restaurant restaurant = new Restaurant();
        restaurant.setName("Public Menu Restaurant");
        restaurant.setCreatorId(1L);
        restaurant = restaurantRepository.saveAndFlush(restaurant);

        MenuItem available = menuItem(restaurant, "Available Dish", MenuItem.Status.AVAILABLE);
        MenuItem soldOut = menuItem(restaurant, "Sold Out Dish", MenuItem.Status.SOLD_OUT);
        MenuItem discontinued = menuItem(restaurant, "Discontinued Dish", MenuItem.Status.DISCONTINUED);
        MenuItem archived = menuItem(restaurant, "Archived Dish", MenuItem.Status.ARCHIVED);
        menuItemRepository.saveAllAndFlush(List.of(available, soldOut, discontinued, archived));

        var publicItems = menuItemRepository.findPageByRestaurantIdAndStatusAndRestaurantLifecycleStatusNot(
                restaurant.getId(), MenuItem.Status.AVAILABLE, RestaurantStatus.ARCHIVED,
                PageRequest.of(0, 24));
        assertThat(publicItems.getContent()).extracting(MenuItem::getName).containsExactly("Available Dish");

        restaurant.setLifecycleStatus(RestaurantStatus.ARCHIVED);
        restaurantRepository.saveAndFlush(restaurant);

        assertThat(menuItemRepository.findPageByRestaurantIdAndStatusAndRestaurantLifecycleStatusNot(
                restaurant.getId(), MenuItem.Status.AVAILABLE, RestaurantStatus.ARCHIVED,
                PageRequest.of(0, 24))).isEmpty();
        assertThat(menuItemRepository.findPageByRestaurantId(restaurant.getId(), PageRequest.of(0, 24))
                .getContent()).hasSize(4);
    }

    @Test
    void ownerQueriesPreferPrincipalAndOnlyUseLegacyIdForUnmigratedRows() {
        Restaurant principalOwned = restaurant("Principal owned", 7L, 101L);
        Restaurant foreignPrincipalSameLegacy = restaurant("Foreign principal", 7L, 202L);
        Restaurant unmigratedLegacy = restaurant("Legacy row", 7L, null);
        Restaurant unrelatedLegacy = restaurant("Other legacy row", 8L, null);
        restaurantRepository.saveAll(List.of(
                principalOwned, foreignPrincipalSameLegacy, unmigratedLegacy, unrelatedLegacy));
        restaurantRepository.flush();

        assertThat(restaurantRepository.findByOwnerPrincipalOrUnmigratedCreator(
                101L, 7L, PageRequest.of(0, 24)).getContent())
                .extracting(Restaurant::getName)
                .containsExactlyInAnyOrder("Principal owned", "Legacy row");
        assertThat(restaurantRepository.findByOwnerPrincipalId(101L, PageRequest.of(0, 24)).getContent())
                .extracting(Restaurant::getName)
                .containsExactly("Principal owned");
    }

    private MenuItem menuItem(Restaurant restaurant, String name, MenuItem.Status status) {
        MenuItem item = new MenuItem();
        item.setRestaurant(restaurant);
        item.setName(name);
        item.setPrice(BigDecimal.TEN);
        item.setStatus(status);
        return item;
    }

    private Restaurant restaurant(String name, Long legacyCreatorId, Long principalId) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(name);
        restaurant.setCreatorId(legacyCreatorId);
        restaurant.setOwnerPrincipalId(principalId);
        return restaurant;
    }
}
