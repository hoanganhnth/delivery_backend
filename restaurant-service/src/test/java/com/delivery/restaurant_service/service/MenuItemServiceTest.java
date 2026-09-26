package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemRequest;
import com.delivery.restaurant_service.dto.response.MenuItemResponse;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.exception.ResourceNotFoundException;
import com.delivery.restaurant_service.mapper.MenuItemMapper;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.service.impl.MenuItemServiceImpl;
import com.delivery.restaurant_service.service.ownership.ManagementAccess;
import com.delivery.restaurant_service.service.ownership.RestaurantOwnershipPolicy;
import com.delivery.restaurant_service.service.impl.CatalogLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuItemServiceTest {

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private MenuItemMapper menuItemMapper;
    @Mock
    private CatalogCacheSynchronizer cacheSynchronizer;
    @Mock
    private SearchSyncPublisher searchSyncPublisher;
    @Mock
    private CatalogLifecycleService catalogLifecycleService;
    @org.mockito.Spy
    private RestaurantOwnershipPolicy restaurantOwnershipPolicy = new RestaurantOwnershipPolicy(false);

    @InjectMocks
    private MenuItemServiceImpl menuItemService;

    private Restaurant restaurant;
    private MenuItem menuItem;
    private CreateMenuItemRequest createRequest;
    private MenuItemResponse menuItemResponse;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");
        restaurant.setCreatorId(1L);

        menuItem = new MenuItem();
        menuItem.setId(1L);
        menuItem.setName("Pizza");
        menuItem.setPrice(BigDecimal.valueOf(25.99));
        menuItem.setRestaurant(restaurant);

        createRequest = new CreateMenuItemRequest();
        createRequest.setName("Pizza");
        createRequest.setPrice(BigDecimal.valueOf(25.99));
        createRequest.setRestaurantId(1L);

        menuItemResponse = new MenuItemResponse();
        menuItemResponse.setName("Pizza");
        menuItemResponse.setPrice(BigDecimal.valueOf(25.99));
    }

    @Test
    void createMenuItem_ShouldReturnMenuItemResponse_WhenValidRequest() {
        // Given
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(menuItemMapper.toEntity(any(CreateMenuItemRequest.class))).thenReturn(menuItem);
        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(menuItem);
        when(menuItemMapper.toResponse(any(MenuItem.class))).thenReturn(menuItemResponse);
        // When
        MenuItemResponse response = menuItemService.createMenuItem(createRequest, 1L, "SHOP_OWNER");

        // Then
        assertNotNull(response);
        assertEquals("Pizza", response.getName());
        assertEquals(BigDecimal.valueOf(25.99), response.getPrice());
        verify(restaurantRepository).findById(1L);
        verify(menuItemRepository).save(any(MenuItem.class));
    }

    @Test
    void createMenuItem_ShouldThrowException_WhenRestaurantNotFound() {
        // Given
        when(restaurantRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () ->
                menuItemService.createMenuItem(createRequest, 1L, "SHOP_OWNER"));

        verify(restaurantRepository).findById(1L);
        verify(menuItemRepository, never()).save(any());
    }

    @Test
    void createMenuItem_ShouldThrowException_WhenUserNotOwner() {
        // Given
        restaurant.setCreatorId(2L); // Different owner

        // When & Then
        assertThrows(AccessDeniedException.class, () ->
                menuItemService.createMenuItem(createRequest, 1L, null));

        verify(restaurantRepository,never()).findById(1L);
        verify(menuItemRepository, never()).save(any());
    }

    @Test
    void createMenuItem_UsesPrincipalAndLegacyIdentityToAuthorizeTheRestaurant() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(menuItemMapper.toEntity(any(CreateMenuItemRequest.class))).thenReturn(menuItem);
        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(menuItem);
        when(menuItemMapper.toResponse(any(MenuItem.class))).thenReturn(menuItemResponse);

        menuItemService.createMenuItem(createRequest, 70L, 1L, RoleConstants.OWNER);

        verify(restaurantOwnershipPolicy).assertCanManage(restaurant, 70L, 1L, RoleConstants.OWNER);
    }

    @Test
    void createMenuItem_AllowsAdminToManageRestaurantOwnedByAnotherPrincipal() {
        restaurant.setOwnerPrincipalId(99L);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(menuItemMapper.toEntity(createRequest)).thenReturn(menuItem);
        when(menuItemRepository.save(menuItem)).thenReturn(menuItem);
        when(menuItemMapper.toResponse(menuItem)).thenReturn(menuItemResponse);

        assertSame(menuItemResponse, menuItemService.createMenuItem(
                createRequest, 7L, 7L, RoleConstants.ADMIN));

        verify(restaurantOwnershipPolicy).assertCanManage(restaurant, 7L, 7L, RoleConstants.ADMIN);
        assertSame(restaurant, menuItem.getRestaurant());
    }

    @Test
    void getItemsByRestaurant_ShouldReturnList_WhenRestaurantExists() {
        // Given
        List<MenuItem> menuItems = Collections.singletonList(menuItem);
        when(menuItemRepository.findByRestaurantId(eq(1L), any())).thenReturn(menuItems);
        when(menuItemMapper.toResponse(any(MenuItem.class))).thenReturn(menuItemResponse);

        // When
        List<MenuItemResponse> responses = menuItemService.getItemsByRestaurant(1L);

        // Then
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("Pizza", responses.get(0).getName());
        verify(menuItemRepository).findByRestaurantId(eq(1L), any());
    }

    @Test
    void deleteMenuItem_ShouldArchiveWithoutPhysicalDeletion_WhenUserIsOwner() {
        // Given
        // When
        menuItemService.deleteMenuItem(1L, 1L, RoleConstants.OWNER);

        // Then
        verify(catalogLifecycleService).archiveMenuItem(1L, 1L, 1L, RoleConstants.OWNER);
    }

    @Test
    void deleteMenuItem_DelegatesOwnershipAndLifecycleToCatalogBoundary() {
        menuItemService.deleteMenuItem(1L, 1L, RoleConstants.OWNER);

        verify(catalogLifecycleService).archiveMenuItem(1L, 1L, 1L, RoleConstants.OWNER);
        verifyNoInteractions(menuItemRepository);
    }

    @Test
    void deleteMenuItem_ShouldAllowAdmin_WhenAdminIsNotOwner() {
        restaurant.setCreatorId(2L);
        menuItemService.deleteMenuItem(1L, 99L, RoleConstants.ADMIN);

        verify(catalogLifecycleService).archiveMenuItem(1L, 99L, 99L, RoleConstants.ADMIN);
    }

    @Test
    void deleteMenuItem_PassesMissingRoleToCatalogBoundary() {
        menuItemService.deleteMenuItem(1L, 1L, null);

        verify(catalogLifecycleService).archiveMenuItem(1L, 1L, 1L, null);
        verifyNoInteractions(menuItemRepository);
    }

    @Test
    void deleteMenuItem_IsIdempotentWhenAlreadyArchived() {
        menuItemService.deleteMenuItem(1L, 1L, RoleConstants.OWNER);

        verify(catalogLifecycleService).archiveMenuItem(1L, 1L, 1L, RoleConstants.OWNER);
    }

    @Test
    void updateMenuItem_ShouldUpdateSuccessfully_WhenValidRequest() {
        // Given
        UpdateMenuItemRequest updateRequest = new UpdateMenuItemRequest();
        updateRequest.setName("Updated Pizza");
        updateRequest.setPrice(BigDecimal.valueOf(29.99));

        MenuItem updatedMenuItem = new MenuItem();
        updatedMenuItem.setId(1L);
        updatedMenuItem.setName("Updated Pizza");
        updatedMenuItem.setPrice(BigDecimal.valueOf(29.99));
        updatedMenuItem.setRestaurant(restaurant);

        MenuItemResponse expectedResponse = new MenuItemResponse( );
        expectedResponse.setId(0L);
        expectedResponse.setRestaurantId(0L);
        expectedResponse.setName("Updated Pizza");
        expectedResponse.setPrice(BigDecimal.valueOf(29.99));

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(menuItem));
        when(menuItemRepository.saveAndFlush(any(MenuItem.class))).thenReturn(updatedMenuItem);
        when(menuItemMapper.toResponse(any(MenuItem.class))).thenReturn(expectedResponse);
        // When
        MenuItemResponse response = menuItemService.updateMenuItem(1L, updateRequest, 1L, RoleConstants.OWNER);

        // Then
        assertNotNull(response);
        assertEquals("Updated Pizza", response.getName());
        assertEquals(BigDecimal.valueOf(29.99), response.getPrice());
        verify(menuItemRepository).findById(1L);
        verify(menuItemRepository).saveAndFlush(any(MenuItem.class));
        verify(menuItemMapper).updateEntityFromDto(any(UpdateMenuItemRequest.class), any(MenuItem.class));
    }

    @Test
    void updateMenuItem_ShouldReturnNotFoundWithoutMutation() {
        when(menuItemRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> menuItemService.updateMenuItem(
                404L, new UpdateMenuItemRequest(), 1L, RoleConstants.OWNER));

        verify(menuItemRepository, never()).saveAndFlush(any());
        verifyNoInteractions(menuItemMapper, cacheSynchronizer, searchSyncPublisher);
    }

    @Test
    void updateMenuItem_ShouldRejectForeignOwnerWithoutMutation() {
        restaurant.setOwnerPrincipalId(99L);
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(menuItem));

        assertThrows(AccessDeniedException.class, () -> menuItemService.updateMenuItem(
                1L, new UpdateMenuItemRequest(), 7L, 7L, RoleConstants.OWNER));

        verify(menuItemRepository, never()).saveAndFlush(any());
        verifyNoInteractions(menuItemMapper, cacheSynchronizer, searchSyncPublisher);
    }

    @Test
    void updateMenuItem_ShouldAllowAdminAcrossOwnershipBoundary() {
        restaurant.setOwnerPrincipalId(99L);
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(menuItem));
        when(menuItemRepository.saveAndFlush(menuItem)).thenReturn(menuItem);
        when(menuItemMapper.toResponse(menuItem)).thenReturn(menuItemResponse);

        MenuItemResponse result = menuItemService.updateMenuItem(
                1L, new UpdateMenuItemRequest(), 7L, 7L, RoleConstants.ADMIN);

        assertSame(menuItemResponse, result);
        verify(menuItemMapper).updateEntityFromDto(any(UpdateMenuItemRequest.class), eq(menuItem));
        verify(menuItemRepository).saveAndFlush(menuItem);
    }

    @Test
    void getManagedItemsPage_ShouldRejectInvalidPaginationBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class, () -> menuItemService.getManagedItemsPage(
                null, 1L, 1L, RoleConstants.OWNER, 0, 101));

        verifyNoInteractions(menuItemRepository, restaurantRepository, menuItemMapper);
    }

    @Test
    void getManagedItemsByRestaurant_ShouldRejectForeignOwnerBeforeReadingMenu() {
        restaurant.setOwnerPrincipalId(99L);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));

        assertThrows(AccessDeniedException.class, () -> menuItemService.getManagedItemsByRestaurant(
                1L, 7L, 7L, RoleConstants.OWNER));

        verifyNoInteractions(menuItemRepository, menuItemMapper);
    }

    @Test
    void getManagedItemsByRestaurant_ShouldAllowAdminToReadArchivedMenuItem() {
        restaurant.setOwnerPrincipalId(99L);
        menuItem.setStatus(MenuItem.Status.ARCHIVED);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findByRestaurantId(eq(1L), any())).thenReturn(List.of(menuItem));
        when(menuItemMapper.toResponse(menuItem)).thenReturn(menuItemResponse);

        assertEquals(List.of(menuItemResponse), menuItemService.getManagedItemsByRestaurant(
                1L, 7L, 7L, RoleConstants.ADMIN));

        verify(menuItemRepository).findByRestaurantId(eq(1L), any());
    }

    @Test
    void getAvailableItems_ShouldReturnOnlyAvailableItems() {
        // Given
        MenuItem availableItem = new MenuItem();
        availableItem.setId(1L);
        availableItem.setName("Available Pizza");
        availableItem.setStatus(MenuItem.Status.AVAILABLE);
        availableItem.setRestaurant(restaurant);
        MenuItemResponse expectedResponse = new MenuItemResponse();
        expectedResponse.setId(1L);
        expectedResponse.setRestaurantId(1L);
        expectedResponse.setName("Available Pizza");
        expectedResponse.setStatus(MenuItem.Status.AVAILABLE.name());



        List<MenuItem> availableItems = List.of(availableItem);
        when(menuItemRepository.findByRestaurantIdAndStatusAndRestaurantLifecycleStatusNot(
                eq(1L), eq(MenuItem.Status.AVAILABLE),
                eq(com.delivery.restaurant.domain.catalog.RestaurantStatus.ARCHIVED), any()))
                .thenReturn(availableItems);
        when(menuItemMapper.toResponse(any(MenuItem.class))).thenReturn(expectedResponse);
        // When
        List<MenuItemResponse> responses = menuItemService.getAvailableItems(1L);

        // Then
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("Available Pizza", responses.get(0).getName());
        verify(menuItemRepository).findByRestaurantIdAndStatusAndRestaurantLifecycleStatusNot(
                eq(1L), eq(MenuItem.Status.AVAILABLE),
                eq(com.delivery.restaurant.domain.catalog.RestaurantStatus.ARCHIVED), any());
    }
}
