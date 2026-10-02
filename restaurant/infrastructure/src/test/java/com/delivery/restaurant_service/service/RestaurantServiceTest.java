package com.delivery.restaurant_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.delivery.restaurant.application.DefaultCreateRestaurantUseCase;
import com.delivery.restaurant.application.DefaultRestaurantManagementAccessUseCase;
import com.delivery.restaurant.application.DefaultRestaurantOwnerAssignmentUseCase;
import com.delivery.restaurant.application.api.CreateRestaurantCommand;
import com.delivery.restaurant.application.api.CreateRestaurantResult;
import com.delivery.restaurant.application.api.PrincipalOwnershipDirectory;
import com.delivery.restaurant.application.api.RestaurantCreationPort;
import com.delivery.restaurant.application.api.RestaurantManagementReadUseCase;
import com.delivery.restaurant.application.api.RestaurantManagementResult;
import com.delivery.restaurant.application.api.RestaurantPageSlice;
import com.delivery.restaurant.application.api.RestaurantReadUseCase;
import com.delivery.restaurant.application.api.RestaurantSnapshot;
import com.delivery.restaurant.application.api.RestaurantUpdateResult;
import com.delivery.restaurant.application.api.UpdateRestaurantCommand;
import com.delivery.restaurant.application.api.UpdateRestaurantUseCase;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
import com.delivery.restaurant_service.dto.response.RestaurantResponse;
import com.delivery.restaurant.domain.catalog.ResourceNotFoundException;
import com.delivery.restaurant_service.mapper.RestaurantMapper;
import com.delivery.restaurant.application.api.CatalogLifecycleUseCase;
import com.delivery.restaurant_service.service.impl.RestaurantServiceImpl;
import com.delivery.restaurant_service.service.ownership.RestaurantOwnershipPolicy;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantMapper restaurantMapper;
    @Mock
    private CatalogLifecycleUseCase catalogLifecycleService;
    @Mock
    private RestaurantCreationPort creationPort;
    @Mock
    private PrincipalOwnershipDirectory directory;
    @Mock
    private UpdateRestaurantUseCase updateRestaurantUseCase;
    @Mock
    private RestaurantReadUseCase restaurantReadUseCase;
    @Mock
    private RestaurantManagementReadUseCase restaurantManagementReadUseCase;

    private final RestaurantOwnershipPolicy restaurantOwnershipPolicy =
            new RestaurantOwnershipPolicy(false, new DefaultRestaurantManagementAccessUseCase());
    private RestaurantServiceImpl restaurantService;
    private io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry;
    private CreateRestaurantRequest createRequest;
    private RestaurantResponse restaurantResponse;

    @BeforeEach
    void setUp() {
        meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        restaurantService = new RestaurantServiceImpl(restaurantMapper,
                meterRegistry,
                restaurantOwnershipPolicy, catalogLifecycleService,
                new DefaultCreateRestaurantUseCase(
                        new DefaultRestaurantOwnerAssignmentUseCase(directory), creationPort),
                updateRestaurantUseCase, restaurantReadUseCase, restaurantManagementReadUseCase);
        createRequest = new CreateRestaurantRequest();
        createRequest.setName("Test Restaurant");
        createRequest.setAddress("123 Test Street");

        restaurantResponse = new RestaurantResponse();
        restaurantResponse.setId(1L);
        restaurantResponse.setName("Test Restaurant");
        restaurantResponse.setAddress("123 Test Street");
    }

    @Test
    void createRestaurantMapsCommandAndApplicationResult() {
        CreateRestaurantResult saved = creationResult();
        when(creationPort.create(any(), eq(1L))).thenReturn(saved);
        when(restaurantMapper.toResponse(saved)).thenReturn(restaurantResponse);

        RestaurantResponse response = restaurantService.createRestaurant(
                createRequest, 1L, 1L, RoleConstants.OWNER);

        assertNotNull(response);
        assertEquals("Test Restaurant", response.getName());
        verify(creationPort).create(any(CreateRestaurantCommand.class), eq(1L));
        verifyNoInteractions(directory, updateRestaurantUseCase,
                restaurantReadUseCase, restaurantManagementReadUseCase);
    }

    @Test
    void createRestaurantPreservesPrincipalAndLegacyCreator() {
        CreateRestaurantResult saved = creationResult();
        when(creationPort.create(any(), eq(70L))).thenReturn(saved);
        when(restaurantMapper.toResponse(saved)).thenReturn(restaurantResponse);

        assertSame(restaurantResponse, restaurantService.createRestaurant(
                createRequest, 70L, 7L, RoleConstants.OWNER));

        ArgumentCaptor<CreateRestaurantCommand> command = ArgumentCaptor.forClass(CreateRestaurantCommand.class);
        verify(creationPort).create(command.capture(), eq(70L));
        assertEquals(70L, command.getValue().actorPrincipalId());
        assertEquals(7L, command.getValue().creatorId());
    }

    @Test
    void createRestaurantRejectsInvalidActorOrScheduleBeforePersistence() {
        assertThrows(RuntimeException.class, () -> restaurantService.createRestaurant(
                createRequest, null, null, RoleConstants.OWNER));
        createRequest.setOpeningHour(LocalTime.of(18, 0));
        assertThrows(IllegalArgumentException.class, () -> restaurantService.createRestaurant(
                createRequest, 1L, 1L, RoleConstants.OWNER));
        verifyNoInteractions(creationPort, updateRestaurantUseCase, restaurantReadUseCase,
                restaurantManagementReadUseCase);
    }

    @Test
    void detailUsesApplicationReadAndPreservesArchivedHistoryAnd404() {
        RestaurantSnapshot archived = snapshot(RestaurantStatus.ARCHIVED);
        when(restaurantReadUseCase.findById(1L)).thenReturn(Optional.of(archived));
        when(restaurantMapper.toResponse(archived)).thenReturn(restaurantResponse);

        assertSame(restaurantResponse, restaurantService.getRestaurantById(1L));
        verify(restaurantReadUseCase).findById(1L);

        when(restaurantReadUseCase.findById(404L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> restaurantService.getRestaurantById(404L));
    }

    @Test
    void updateMapsNullablePatchAndPreservesApplicationResult() {
        UpdateRestaurantRequest request = new UpdateRestaurantRequest();
        request.setName("Renamed Restaurant");
        RestaurantSnapshot snapshot = snapshot(RestaurantStatus.ACTIVE);
        when(updateRestaurantUseCase.update(any(UpdateRestaurantCommand.class)))
                .thenReturn(Optional.of(new RestaurantUpdateResult(snapshot, false)));
        when(restaurantMapper.toResponse(snapshot)).thenReturn(restaurantResponse);

        assertSame(restaurantResponse, restaurantService.updateRestaurant(
                1L, request, 7L, 700L, RoleConstants.OWNER));

        ArgumentCaptor<UpdateRestaurantCommand> command = ArgumentCaptor.forClass(UpdateRestaurantCommand.class);
        verify(updateRestaurantUseCase).update(command.capture());
        assertEquals("Renamed Restaurant", command.getValue().name());
        assertEquals(7L, command.getValue().actorPrincipalId());
        assertEquals(700L, command.getValue().legacyUserId());
        verifyNoInteractions(directory);
    }

    @Test
    void updateMapsMissingAndAuthorizationFailuresWithoutWrite() {
        when(updateRestaurantUseCase.update(any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> restaurantService.updateRestaurant(
                404L, new UpdateRestaurantRequest(), 7L, 7L, RoleConstants.OWNER));

        when(updateRestaurantUseCase.update(any()))
                .thenThrow(new ManagementAccessException(
                        com.delivery.restaurant.domain.ownership.ManagementAccessFailure.ACTOR_DOES_NOT_OWN_RESTAURANT));
        assertThrows(AccessDeniedException.class, () -> restaurantService.updateRestaurant(
                1L, new UpdateRestaurantRequest(), 7L, 7L, RoleConstants.OWNER));
        verifyNoInteractions(directory);
    }

    @Test
    void updateEmitsHostMetricForLegacyOwnershipClaim() {
        RestaurantSnapshot snapshot = snapshot(RestaurantStatus.ACTIVE);
        when(updateRestaurantUseCase.update(any()))
                .thenReturn(Optional.of(new RestaurantUpdateResult(snapshot, true)));
        when(restaurantMapper.toResponse(snapshot)).thenReturn(restaurantResponse);

        restaurantService.updateRestaurant(1L, new UpdateRestaurantRequest(),
                101L, 7L, RoleConstants.OWNER);
        verify(updateRestaurantUseCase).update(any(UpdateRestaurantCommand.class));
        assertEquals(1.0, meterRegistry.get("delivery.identity.legacy.fallback")
                .tag("service", "restaurant").tag("surface", "owner_manage").counter().count());
    }

    @Test
    void publicReadsUseApplicationProjectionBoundaries() {
        RestaurantSnapshot active = snapshot(RestaurantStatus.ACTIVE);
        when(restaurantReadUseCase.listPublic()).thenReturn(List.of(active));
        when(restaurantReadUseCase.searchPublic("  Test  ")).thenReturn(List.of(active));
        when(restaurantMapper.toResponse(active)).thenReturn(restaurantResponse);

        assertEquals(List.of(restaurantResponse), restaurantService.getAllRestaurants());
        assertEquals(List.of(restaurantResponse), restaurantService.findByName("  Test  "));
        verify(restaurantReadUseCase).searchPublic("  Test  ");
    }

    @Test
    void publicPagePreservesEnvelopeAndMetadata() {
        RestaurantSnapshot active = snapshot(RestaurantStatus.ACTIVE);
        when(restaurantReadUseCase.pagePublic(1, 20, "  pizza  "))
                .thenReturn(new RestaurantPageSlice(List.of(active), 1, 20, 41, 3, true));
        when(restaurantMapper.toResponse(active)).thenReturn(restaurantResponse);

        var page = restaurantService.getAllRestaurantsPage(1, 20, "  pizza  ");

        assertEquals(41, page.getTotalElements());
        assertEquals(1, page.getNumber());
        assertEquals(20, page.getSize());
        verify(restaurantReadUseCase).pagePublic(1, 20, "  pizza  ");
    }

    @Test
    void managementReadsPreserveArchivedRowsAndLegacyFallbackCount() {
        RestaurantSnapshot archived = snapshot(RestaurantStatus.ARCHIVED);
        when(restaurantManagementReadUseCase.readForAdmin(901L, 7L, false))
                .thenReturn(new RestaurantManagementResult(List.of(archived), 0));
        when(restaurantManagementReadUseCase.readForOwner(101L, 7L, false))
                .thenReturn(new RestaurantManagementResult(List.of(archived), 1));
        when(restaurantMapper.toResponse(archived)).thenReturn(restaurantResponse);

        assertEquals(List.of(restaurantResponse), restaurantService.getAllManagedRestaurants(901L, 7L));
        assertEquals(List.of(restaurantResponse),
                restaurantService.getRestaurantsByOwnerPrincipalId(101L, 7L));
        verify(restaurantManagementReadUseCase).readForAdmin(901L, 7L, false);
        verify(restaurantManagementReadUseCase).readForOwner(101L, 7L, false);
    }

    @Test
    void deleteStillDelegatesToArchiveLifecycleBoundary() {
        restaurantService.deleteRestaurant(1L, 1L, 1L, RoleConstants.OWNER);
        verify(catalogLifecycleService).changeRestaurant(1L, com.delivery.restaurant.domain.catalog.RestaurantStatus.ARCHIVED, null, 1L, 1L, RoleConstants.OWNER);
    }

    @Test
    void deleteRestaurant_ShouldAllowAdmin_WhenAdminIsNotOwner() {
        restaurantService.deleteRestaurant(1L, 99L, 99L, RoleConstants.ADMIN);

        verify(catalogLifecycleService).changeRestaurant(1L, com.delivery.restaurant.domain.catalog.RestaurantStatus.ARCHIVED, null, 99L, 99L, RoleConstants.ADMIN);
    }

    @Test
    void deleteRestaurant_PassesMissingRoleToCatalogBoundary() {
        restaurantService.deleteRestaurant(1L, 1L, 1L, null);

        verify(catalogLifecycleService).changeRestaurant(1L, com.delivery.restaurant.domain.catalog.RestaurantStatus.ARCHIVED, null, 1L, 1L, null);
    }

    @Test
    void deleteRestaurant_IsIdempotentWhenAlreadyArchived() {
        restaurantService.deleteRestaurant(1L, 1L, 1L, RoleConstants.OWNER);

        verify(catalogLifecycleService)
                .changeRestaurant(1L, com.delivery.restaurant.domain.catalog.RestaurantStatus.ARCHIVED, null, 1L, 1L, RoleConstants.OWNER);
    }

    private CreateRestaurantResult creationResult() {
        return new CreateRestaurantResult(1L, "Test Restaurant", "123 Test Street", null,
                null, null, 30, null, null, null, null, 0.0, 0,
                RestaurantStatus.ACTIVE, 0L, "Asia/Ho_Chi_Minh");
    }

    private RestaurantSnapshot snapshot(RestaurantStatus status) {
        return new RestaurantSnapshot(1L, "Test Restaurant", "123 Test Street", "0123456789",
                LocalTime.of(9, 0), LocalTime.of(18, 0), 30, "image.png", "Description",
                10.8, 106.7, 4.5, 2, status, 1L, "Asia/Ho_Chi_Minh", 7L, 7L);
    }
}
