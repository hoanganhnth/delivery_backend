package com.delivery.restaurant_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.delivery.restaurant.application.api.CreateMenuItemCommand;
import com.delivery.restaurant.application.api.CreateMenuItemUseCase;
import com.delivery.restaurant.application.api.MenuItemCreateResult;
import com.delivery.restaurant.application.api.MenuItemManagementQuery;
import com.delivery.restaurant.application.api.MenuItemManagementReadUseCase;
import com.delivery.restaurant.application.api.MenuItemPageSlice;
import com.delivery.restaurant.application.api.MenuItemReadUseCase;
import com.delivery.restaurant.application.api.MenuItemSnapshot;
import com.delivery.restaurant.application.api.MenuItemUpdateResult;
import com.delivery.restaurant.application.api.UpdateMenuItemCommand;
import com.delivery.restaurant.application.api.UpdateMenuItemUseCase;
import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.ManagementAccessFailure;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemRequest;
import com.delivery.restaurant_service.dto.response.MenuItemResponse;
import com.delivery.restaurant_service.exception.ResourceNotFoundException;
import com.delivery.restaurant_service.mapper.MenuItemMapper;
import com.delivery.restaurant_service.service.impl.CatalogLifecycleService;
import com.delivery.restaurant_service.service.impl.MenuItemServiceImpl;
import com.delivery.restaurant_service.service.ownership.RestaurantOwnershipPolicy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class MenuItemServiceTest {

    @Mock
    private MenuItemMapper menuItemMapper;
    @Mock
    private RestaurantOwnershipPolicy restaurantOwnershipPolicy;
    @Mock
    private CatalogLifecycleService catalogLifecycleService;
    @Mock
    private CreateMenuItemUseCase createMenuItemUseCase;
    @Mock
    private UpdateMenuItemUseCase updateMenuItemUseCase;
    @Mock
    private MenuItemReadUseCase menuItemReadUseCase;
    @Mock
    private MenuItemManagementReadUseCase menuItemManagementReadUseCase;

    @InjectMocks
    private MenuItemServiceImpl menuItemService;

    private CreateMenuItemRequest createRequest;
    private MenuItemResponse menuItemResponse;
    private MenuItemSnapshot snapshot;

    @BeforeEach
    void setUp() {
        createRequest = new CreateMenuItemRequest();
        createRequest.setRestaurantId(1L);
        createRequest.setName("Pizza");
        createRequest.setDescription("Classic");
        createRequest.setPrice(new BigDecimal("25.99"));
        createRequest.setImage("pizza.jpg");

        menuItemResponse = new MenuItemResponse();
        menuItemResponse.setId(1L);
        menuItemResponse.setRestaurantId(1L);
        menuItemResponse.setName("Pizza");
        menuItemResponse.setPrice(new BigDecimal("25.99"));

        snapshot = new MenuItemSnapshot(1L, 1L, "Pizza", "Classic",
                new BigDecimal("25.99"), MenuItemStatus.AVAILABLE,
                LocalDateTime.MIN, LocalDateTime.MIN, "pizza.jpg", 0L);
        lenient().when(restaurantOwnershipPolicy.isPrincipalOwnershipEnforced()).thenReturn(false);
    }

    @Test
    void createMapsRequestToApplicationCommandAndResultToResponse() {
        MenuItemCreateResult result = new MenuItemCreateResult(snapshot, false);
        when(createMenuItemUseCase.create(any(CreateMenuItemCommand.class)))
                .thenReturn(Optional.of(result));
        when(menuItemMapper.toResponse(result)).thenReturn(menuItemResponse);

        assertSame(menuItemResponse, menuItemService.createMenuItem(
                createRequest, 70L, 7L, RoleConstants.OWNER));

        ArgumentCaptor<CreateMenuItemCommand> command = ArgumentCaptor.forClass(CreateMenuItemCommand.class);
        verify(createMenuItemUseCase).create(command.capture());
        assertEquals(1L, command.getValue().restaurantId());
        assertEquals(70L, command.getValue().actorPrincipalId());
        assertEquals(7L, command.getValue().legacyUserId());
        assertEquals(RestaurantActorRole.SHOP_OWNER, command.getValue().actorRole());
        assertEquals("Pizza", command.getValue().name());
        verify(menuItemMapper).toResponse(result);
    }

    @Test
    void createMapsMissingRestaurantToNotFound() {
        when(createMenuItemUseCase.create(any(CreateMenuItemCommand.class))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> menuItemService.createMenuItem(
                createRequest, 1L, 1L, RoleConstants.OWNER));

        verify(createMenuItemUseCase).create(any(CreateMenuItemCommand.class));
        verifyNoInteractions(menuItemMapper);
    }

    @Test
    void createMapsApplicationAccessFailureToForbidden() {
        when(createMenuItemUseCase.create(any(CreateMenuItemCommand.class)))
                .thenThrow(new ManagementAccessException(ManagementAccessFailure.ACTOR_DOES_NOT_OWN_RESTAURANT));

        assertThrows(AccessDeniedException.class, () -> menuItemService.createMenuItem(
                createRequest, 1L, 2L, RoleConstants.OWNER));

        verifyNoInteractions(menuItemMapper);
    }

    @Test
    void adminCreateSuppliesCompleteIdentityTupleWhenLegacyIdentityIsMissing() {
        MenuItemCreateResult result = new MenuItemCreateResult(snapshot, false);
        when(createMenuItemUseCase.create(any(CreateMenuItemCommand.class)))
                .thenReturn(Optional.of(result));
        when(menuItemMapper.toResponse(result)).thenReturn(menuItemResponse);

        menuItemService.createMenuItem(createRequest, 901L, null, RoleConstants.ADMIN);

        ArgumentCaptor<CreateMenuItemCommand> command = ArgumentCaptor.forClass(CreateMenuItemCommand.class);
        verify(createMenuItemUseCase).create(command.capture());
        assertEquals(901L, command.getValue().legacyUserId());
        assertEquals(RestaurantActorRole.ADMIN, command.getValue().actorRole());
    }

    @Test
    void updateMapsNullablePatchAndRequestedParentWithoutAllowingReparentingInHost() {
        UpdateMenuItemRequest request = new UpdateMenuItemRequest();
        request.setName("Updated Pizza");
        request.setPrice(new BigDecimal("29.99"));
        request.setStatus(com.delivery.restaurant_service.entity.MenuItem.Status.SOLD_OUT);
        request.setRestaurantId(999L);
        MenuItemUpdateResult result = new MenuItemUpdateResult(snapshot, false);
        when(updateMenuItemUseCase.update(any(UpdateMenuItemCommand.class)))
                .thenReturn(Optional.of(result));
        when(menuItemMapper.toResponse(result)).thenReturn(menuItemResponse);

        assertSame(menuItemResponse, menuItemService.updateMenuItem(
                1L, request, 70L, 7L, RoleConstants.OWNER));

        ArgumentCaptor<UpdateMenuItemCommand> command = ArgumentCaptor.forClass(UpdateMenuItemCommand.class);
        verify(updateMenuItemUseCase).update(command.capture());
        assertEquals(1L, command.getValue().menuItemId());
        assertEquals(70L, command.getValue().actorPrincipalId());
        assertEquals("Updated Pizza", command.getValue().name());
        assertEquals(MenuItemStatus.SOLD_OUT, command.getValue().status());
        assertEquals(999L, command.getValue().requestedRestaurantId());
    }

    @Test
    void updateMapsMissingMenuToNotFound() {
        when(updateMenuItemUseCase.update(any(UpdateMenuItemCommand.class))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> menuItemService.updateMenuItem(
                404L, new UpdateMenuItemRequest(), 1L, 1L, RoleConstants.OWNER));

        verify(updateMenuItemUseCase).update(any(UpdateMenuItemCommand.class));
        verifyNoInteractions(menuItemMapper);
    }

    @Test
    void updateMapsApplicationAccessFailureToForbidden() {
        when(updateMenuItemUseCase.update(any(UpdateMenuItemCommand.class)))
                .thenThrow(new ManagementAccessException(ManagementAccessFailure.ACTOR_DOES_NOT_OWN_RESTAURANT));

        assertThrows(AccessDeniedException.class, () -> menuItemService.updateMenuItem(
                1L, new UpdateMenuItemRequest(), 7L, 7L, RoleConstants.OWNER));

        verifyNoInteractions(menuItemMapper);
    }

    @Test
    void publicListDelegatesToApplicationReadAndMapsSnapshots() {
        when(menuItemReadUseCase.listPublic(1L)).thenReturn(List.of(snapshot));
        when(menuItemMapper.toResponse(snapshot)).thenReturn(menuItemResponse);

        assertEquals(List.of(menuItemResponse), menuItemService.getAvailableItems(1L));

        verify(menuItemReadUseCase).listPublic(1L);
        verify(menuItemMapper).toResponse(snapshot);
    }

    @Test
    void publicPageDelegatesToApplicationReadAndPreservesEnvelope() {
        MenuItemPageSlice source = new MenuItemPageSlice(List.of(snapshot), 1, 24, 25, 2, true);
        Page<MenuItemResponse> expected = new PageImpl<>(List.of(menuItemResponse), PageRequest.of(1, 24), 25);
        when(menuItemReadUseCase.pagePublic(1L, 1, 24)).thenReturn(source);
        when(menuItemMapper.toPage(source)).thenReturn(expected);

        assertSame(expected, menuItemService.getAvailableItemsPage(1L, 1, 24));

        verify(menuItemReadUseCase).pagePublic(1L, 1, 24);
        verify(menuItemMapper).toPage(source);
    }

    @Test
    void managementPageDelegatesQueryAndMapsMissingParentToNotFound() {
        when(menuItemManagementReadUseCase.read(any(MenuItemManagementQuery.class)))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> menuItemService.getManagedItemsPage(
                404L, 1L, 1L, RoleConstants.OWNER, 0, 24));

        ArgumentCaptor<MenuItemManagementQuery> query = ArgumentCaptor.forClass(MenuItemManagementQuery.class);
        verify(menuItemManagementReadUseCase).read(query.capture());
        assertEquals(404L, query.getValue().restaurantId());
        assertEquals(1L, query.getValue().principalId());
        assertEquals(24, query.getValue().size());
        verifyNoInteractions(menuItemMapper);
    }

    @Test
    void managementPagePreservesPaginationAndMapsApplicationAccessFailure() {
        when(menuItemManagementReadUseCase.read(any(MenuItemManagementQuery.class)))
                .thenThrow(new ManagementAccessException(ManagementAccessFailure.ACTOR_DOES_NOT_OWN_RESTAURANT));

        assertThrows(AccessDeniedException.class, () -> menuItemService.getManagedItemsPage(
                1L, 7L, 7L, RoleConstants.OWNER, 0, 24));

        verifyNoInteractions(menuItemMapper);
    }

    @Test
    void managementPagePropagatesApplicationPaginationValidation() {
        when(menuItemManagementReadUseCase.read(any(MenuItemManagementQuery.class)))
                .thenThrow(new IllegalArgumentException("Invalid page or size"));

        assertThrows(IllegalArgumentException.class, () -> menuItemService.getManagedItemsPage(
                null, 1L, 1L, RoleConstants.OWNER, 0, 101));

        verify(menuItemManagementReadUseCase).read(any(MenuItemManagementQuery.class));
        verifyNoInteractions(menuItemMapper);
    }

    @Test
    void deleteStillDelegatesToLifecycleBoundary() {
        menuItemService.deleteMenuItem(1L, 1L, 1L, RoleConstants.OWNER);

        verify(catalogLifecycleService).archiveMenuItem(1L, 1L, 1L, RoleConstants.OWNER);
        verifyNoInteractions(createMenuItemUseCase, updateMenuItemUseCase,
                menuItemReadUseCase, menuItemManagementReadUseCase);
    }
}
