package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.HttpHeaderConstants;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemRequest;
import com.delivery.restaurant_service.dto.response.MenuItemResponse;
import com.delivery.restaurant_service.service.MenuItemService;
import com.delivery.restaurant_service.service.impl.MenuItemServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MenuItemControllerTest {

    @Mock
    private MenuItemServiceImpl menuItemService;

    @InjectMocks
    private MenuItemController menuItemController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(menuItemController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void create_ShouldReturnCreatedMenuItem_WhenValidRequest() throws Exception {
        // Given
        CreateMenuItemRequest request = new CreateMenuItemRequest();
        request.setName("Pizza Margherita");
        request.setDescription("Classic Italian pizza");
        request.setPrice(BigDecimal.valueOf(25.99));
        request.setRestaurantId(1L);

        MenuItemResponse response = new MenuItemResponse();
        response.setId(1L);
        response.setName("Pizza Margherita");
        response.setDescription("Classic Italian pizza");
        response.setPrice(BigDecimal.valueOf(25.99));

        when(menuItemService.createMenuItem(any(CreateMenuItemRequest.class), anyLong(), anyString()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/menu-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaderConstants.X_USER_ID, "1")
                        .header(HttpHeaderConstants.X_ROLE, RoleConstants.OWNER)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Pizza Margherita"))
                .andExpect(jsonPath("$.data.description").value("Classic Italian pizza"))
                .andExpect(jsonPath("$.data.price").value(25.99));

        verify(menuItemService).createMenuItem(any(CreateMenuItemRequest.class), eq(1L), eq(RoleConstants.OWNER));
    }

    @Test
    void update_ShouldReturnUpdatedMenuItem_WhenValidRequest() throws Exception {
        // Given
        Long menuItemId = 1L;
        UpdateMenuItemRequest request = new UpdateMenuItemRequest();
        request.setName("Updated Pizza");
        request.setPrice(BigDecimal.valueOf(29.99));

        MenuItemResponse response = new MenuItemResponse();
        response.setId(menuItemId);
        response.setName("Updated Pizza");
        response.setPrice(BigDecimal.valueOf(29.99));

        when(menuItemService.updateMenuItem(eq(menuItemId), any(UpdateMenuItemRequest.class), anyLong()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(put("/api/menu-items/{id}", menuItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaderConstants.X_USER_ID, "1")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Updated Pizza"))
                .andExpect(jsonPath("$.data.price").value(29.99));

        verify(menuItemService).updateMenuItem(eq(menuItemId), any(UpdateMenuItemRequest.class), eq(1L));
    }

    @Test
    void delete_ShouldReturnSuccess_WhenValidId() throws Exception {
        // Given
        Long menuItemId = 1L;
        Long userId = 1L;

        // When & Then
        mockMvc.perform(delete("/api/menu-items/{id}", menuItemId)
                        .header(HttpHeaderConstants.X_USER_ID, userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(menuItemService).deleteMenuItem(eq(menuItemId), eq(userId));
    }

    @Test
    void getByRestaurant_ShouldReturnMenuItems_WhenValidRestaurantId() throws Exception {
        // Given
        Long restaurantId = 1L;
        List<MenuItemResponse> menuItems = Arrays.asList(
                createMenuItemResponse(1L, "Pizza", BigDecimal.valueOf(25.99)),
                createMenuItemResponse(2L, "Burger", BigDecimal.valueOf(15.99))
        );

        when(menuItemService.getItemsByRestaurant(restaurantId)).thenReturn(menuItems);

        // When & Then
        mockMvc.perform(get("/api/menu-items/restaurant/{restaurantId}", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Pizza"))
                .andExpect(jsonPath("$.data[1].id").value(2))
                .andExpect(jsonPath("$.data[1].name").value("Burger"));

        verify(menuItemService).getItemsByRestaurant(restaurantId);
    }

    @Test
    void getAvailableItems_ShouldReturnAvailableMenuItems_WhenValidRestaurantId() throws Exception {
        // Given
        Long restaurantId = 1L;
        List<MenuItemResponse> availableItems = Arrays.asList(
                createMenuItemResponse(1L, "Available Pizza", BigDecimal.valueOf(25.99))
        );

        when(menuItemService.getAvailableItems(restaurantId)).thenReturn(availableItems);

        // When & Then
        mockMvc.perform(get("/api/menu-items/restaurant/{restaurantId}/available", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Available Pizza"));

        verify(menuItemService).getAvailableItems(restaurantId);
    }

    @Test
    void create_ShouldWork_WithoutHeaders() throws Exception {
        // Given
        CreateMenuItemRequest request = new CreateMenuItemRequest();
        request.setName("Pizza");
        request.setPrice(BigDecimal.valueOf(25.99));

        MenuItemResponse response = createMenuItemResponse(1L, "Pizza", BigDecimal.valueOf(25.99));

        when(menuItemService.createMenuItem(any(CreateMenuItemRequest.class), isNull(), isNull()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/menu-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data.name").value("Pizza"));

        verify(menuItemService).createMenuItem(any(CreateMenuItemRequest.class), isNull(), isNull());
    }

    private MenuItemResponse createMenuItemResponse(Long id, String name, BigDecimal price) {
        MenuItemResponse response = new MenuItemResponse();
        response.setId(id);
        response.setName(name);
        response.setPrice(price);
        return response;
    }
}