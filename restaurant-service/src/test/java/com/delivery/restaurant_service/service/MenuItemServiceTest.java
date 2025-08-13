package com.delivery.restaurant_service.service;

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
    void getItemsByRestaurant_ShouldReturnList_WhenRestaurantExists() {
        // Given
        List<MenuItem> menuItems = Collections.singletonList(menuItem);
        when(menuItemRepository.findByRestaurantId(1L)).thenReturn(menuItems);
        when(menuItemMapper.toResponse(any(MenuItem.class))).thenReturn(menuItemResponse);

        // When
        List<MenuItemResponse> responses = menuItemService.getItemsByRestaurant(1L);

        // Then
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("Pizza", responses.get(0).getName());
        verify(menuItemRepository).findByRestaurantId(1L);
    }

    @Test
    void deleteMenuItem_ShouldDeleteSuccessfully_WhenUserIsOwner() {
        // Given
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(menuItem));

        // When
        menuItemService.deleteMenuItem(1L, 1L);

        // Then
        verify(menuItemRepository).findById(1L);
        verify(menuItemRepository).delete(menuItem);
    }

    @Test
    void deleteMenuItem_ShouldThrowException_WhenUserNotOwner() {
        // Given
        restaurant.setCreatorId(2L); // Different owner
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(menuItem));

        // When & Then
        assertThrows(AccessDeniedException.class, () ->
                menuItemService.deleteMenuItem(1L, 1L));

        verify(menuItemRepository).findById(1L);
        verify(menuItemRepository, never()).delete(any());
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
        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(updatedMenuItem);
        when(menuItemMapper.toResponse(any(MenuItem.class))).thenReturn(expectedResponse);
        // When
        MenuItemResponse response = menuItemService.updateMenuItem(1L, updateRequest, 1L);

        // Then
        assertNotNull(response);
        assertEquals("Updated Pizza", response.getName());
        assertEquals(BigDecimal.valueOf(29.99), response.getPrice());
        verify(menuItemRepository).findById(1L);
        verify(menuItemRepository).save(any(MenuItem.class));
        verify(menuItemMapper).updateEntityFromDto(any(UpdateMenuItemRequest.class), any(MenuItem.class));
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
        when(menuItemRepository.findByRestaurantIdAndStatus(1L, MenuItem.Status.AVAILABLE)).thenReturn(availableItems);
        when(menuItemMapper.toResponse(any(MenuItem.class))).thenReturn(expectedResponse);
        // When
        List<MenuItemResponse> responses = menuItemService.getAvailableItems(1L);

        // Then
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("Available Pizza", responses.get(0).getName());
        verify(menuItemRepository).findByRestaurantIdAndStatus(1L, MenuItem.Status.AVAILABLE);
    }
}
