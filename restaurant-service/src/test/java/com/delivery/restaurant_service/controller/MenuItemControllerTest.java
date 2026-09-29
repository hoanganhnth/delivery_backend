package com.delivery.restaurant_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.restaurant_service.common.constants.HttpHeaderConstants;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemRequest;
import com.delivery.restaurant_service.dto.response.MenuItemResponse;
import com.delivery.restaurant_service.service.impl.MenuItemServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        mockMvc = MockMvcBuilders.standaloneSetup(menuItemController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                                && AuthenticatedActor.class.isAssignableFrom(parameter.getParameterType());
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        String userIdHeader = webRequest.getHeader(HttpHeaderConstants.X_USER_ID);
                        String roleHeader = webRequest.getHeader(HttpHeaderConstants.X_ROLE);
                        if (userIdHeader != null && userIdHeader.matches("\\d+")) {
                            Long userId = Long.parseLong(userIdHeader);
                            Set<String> roles = roleHeader != null ? Set.of(roleHeader) : Set.of("USER");
                            return new AuthenticatedActor(userId, "user@example.com", roles);
                        }
                        return null;
                    }
                })
                .build();
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

        when(menuItemService.createMenuItem(any(CreateMenuItemRequest.class), anyLong(), anyLong(), anyString()))
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

        verify(menuItemService).createMenuItem(any(CreateMenuItemRequest.class), eq(1L), eq(1L), eq(RoleConstants.OWNER));
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

        when(menuItemService.updateMenuItem(eq(menuItemId), any(UpdateMenuItemRequest.class), anyLong(), anyLong(),
                eq(RoleConstants.OWNER)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(put("/api/menu-items/{id}", menuItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaderConstants.X_USER_ID, "1")
                        .header(HttpHeaderConstants.X_ROLE, RoleConstants.OWNER)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Updated Pizza"))
                .andExpect(jsonPath("$.data.price").value(29.99));

        verify(menuItemService).updateMenuItem(eq(menuItemId), any(UpdateMenuItemRequest.class), eq(1L), eq(1L),
                eq(RoleConstants.OWNER));
    }

    @Test
    void delete_ShouldReturnSuccess_WhenValidId() throws Exception {
        // Given
        Long menuItemId = 1L;
        Long userId = 1L;

        // When & Then
        mockMvc.perform(delete("/api/menu-items/{id}", menuItemId)
                        .header(HttpHeaderConstants.X_USER_ID, userId.toString())
                        .header(HttpHeaderConstants.X_ROLE, RoleConstants.OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(menuItemService).deleteMenuItem(eq(menuItemId), eq(userId), eq(userId), eq(RoleConstants.OWNER));
    }

    @Test
    void getByRestaurant_ShouldReturnMenuItems_WhenValidRestaurantId() throws Exception {
        // Given
        Long restaurantId = 1L;
        List<MenuItemResponse> menuItems = Arrays.asList(
                createMenuItemResponse(1L, "Pizza", BigDecimal.valueOf(25.99)),
                createMenuItemResponse(2L, "Burger", BigDecimal.valueOf(15.99))
        );

        when(menuItemService.getAvailableItems(restaurantId)).thenReturn(menuItems);

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

        verify(menuItemService).getAvailableItems(restaurantId);
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
    void myMenuItemsRejectsNonOwnerRole() {
        AuthenticatedActor shipperActor = new AuthenticatedActor(7L, "shipper@example.com", Set.of(RoleConstants.SHIPPER));
        assertThatThrownBy(() -> menuItemController.getMyMenuItems(shipperActor))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(menuItemService);
    }

    @Test
    void create_ShouldReject_WithoutAuthenticatedActor() throws Exception {
        // Given
        CreateMenuItemRequest request = new CreateMenuItemRequest();
        request.setRestaurantId(1L);
        request.setName("Pizza");
        request.setPrice(BigDecimal.valueOf(25.99));

        // When & Then - no identity header provided, actor resolved to null, controller throws AccessDeniedException
        assertThatThrownBy(() -> menuItemController.create(request, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(menuItemService);
    }

    @Test
    void create_InvalidPayloadReturnsBadRequestBeforeServiceCall() throws Exception {
        CreateMenuItemRequest request = new CreateMenuItemRequest();
        request.setRestaurantId(0L);
        request.setName(" ");
        request.setPrice(BigDecimal.ZERO);

        mockMvc.perform(post("/api/menu-items")
                        .header(HttpHeaderConstants.X_USER_ID, "1")
                        .header(HttpHeaderConstants.X_ROLE, RoleConstants.OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(menuItemService);
    }

    @Test
    void update_InvalidPayloadReturnsBadRequestBeforeServiceCall() throws Exception {
        UpdateMenuItemRequest request = new UpdateMenuItemRequest();
        request.setName(" ");

        mockMvc.perform(put("/api/menu-items/{id}", 1L)
                        .header(HttpHeaderConstants.X_USER_ID, "1")
                        .header(HttpHeaderConstants.X_ROLE, RoleConstants.OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(menuItemService);
    }

    @Test
    void page_InvalidPaginationReturnsBadRequestBeforeServiceCall() throws Exception {
        assertThatThrownBy(() -> menuItemController.getPage(1L, -1, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid page or size");

        verifyNoInteractions(menuItemService);
    }

    @Test
    void managementPage_RejectsUnauthenticatedActorBeforeServiceCall() throws Exception {
        assertThatThrownBy(() -> menuItemController.getMyMenuItemsPage(null, null, 0, 24))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(menuItemService);
    }

    @Test
    void publicPagePreservesPaginationEnvelopeAndOnlyRequestsAvailableItems() throws Exception {
        MenuItemResponse item = createMenuItemResponse(9L, "Pho", BigDecimal.valueOf(45000));
        when(menuItemService.getAvailableItemsPage(4L, 1, 1))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(1, 1), 2));

        mockMvc.perform(get("/api/menu-items/restaurant/{restaurantId}/page", 4L)
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(9))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        verify(menuItemService).getAvailableItemsPage(4L, 1, 1);
    }

    @Test
    void managementPagePassesAuthenticatedOwnerIdentityAndRequestedPage() throws Exception {
        when(menuItemService.getManagedItemsPage(4L, 1L, 1L, RoleConstants.OWNER, 2, 5))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 11));

        mockMvc.perform(get("/api/menu-items/my-menu-items/page")
                        .header(HttpHeaderConstants.X_USER_ID, "1")
                        .header(HttpHeaderConstants.X_ROLE, RoleConstants.OWNER)
                        .param("restaurantId", "4")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.totalItems").value(11))
                .andExpect(jsonPath("$.data.totalPages").value(3));

        verify(menuItemService).getManagedItemsPage(4L, 1L, 1L, RoleConstants.OWNER, 2, 5);
    }

    private MenuItemResponse createMenuItemResponse(Long id, String name, BigDecimal price) {
        MenuItemResponse response = new MenuItemResponse();
        response.setId(id);
        response.setName(name);
        response.setPrice(price);
        return response;
    }
}
