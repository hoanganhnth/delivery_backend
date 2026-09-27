package com.delivery.restaurant_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.restaurant_service.service.MenuItemService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import java.util.Set;
import static org.mockito.Mockito.*;

class MenuCatalogBoundaryTest {
    @Test void publicListUsesAvailableItems() {
        var service = mock(MenuItemService.class);
        new MenuItemController(service).getByRestaurant(1L);
        verify(service).getAvailableItems(1L);
    }
    @Test void publicPageUsesAvailableItems() {
        var service = mock(MenuItemService.class);
        when(service.getAvailableItemsPage(anyLong(), anyInt(), anyInt()))
                .thenReturn(Page.empty());
        new MenuItemController(service).getPage(1L, 0, 24);
        verify(service).getAvailableItemsPage(1L, 0, 24);
    }
    @Test void ownerListMustNotUseLegacyOnlyQuery() {
        var service = mock(MenuItemService.class);
        when(service.getManagedItemsPage(null, 101L, 7L, "SHOP_OWNER", 0, 100)).thenReturn(Page.empty());
        new MenuItemController(service).getMyMenuItems(
                new AuthenticatedActor(101L, 7L, "owner@test", Set.of("SHOP_OWNER")));
        verify(service).getManagedItemsPage(null, 101L, 7L, "SHOP_OWNER", 0, 100);
    }
}
