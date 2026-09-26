package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
import com.delivery.restaurant_service.dto.response.RestaurantResponse;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant.application.DefaultCreateRestaurantUseCase;
import com.delivery.restaurant.application.DefaultRestaurantOwnerAssignmentUseCase;
import com.delivery.restaurant.application.api.CreateRestaurantCommand;
import com.delivery.restaurant.application.api.CreateRestaurantResult;
import com.delivery.restaurant.application.api.PrincipalOwnershipDirectory;
import com.delivery.restaurant.application.api.RestaurantCreationPort;
import com.delivery.restaurant.domain.ownership.OwnerAssignmentException;
import com.delivery.restaurant_service.exception.ResourceNotFoundException;
import com.delivery.restaurant_service.mapper.RestaurantMapper;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.service.impl.RestaurantServiceImpl;
import com.delivery.restaurant_service.service.impl.CatalogLifecycleService;
import com.delivery.restaurant_service.service.ownership.RestaurantOwnershipPolicy;
import com.delivery.restaurant_service.service.ownership.ManagementAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.time.LocalTime;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private CatalogCacheSynchronizer cacheSynchronizer;
    @Mock
    private SearchSyncPublisher searchSyncPublisher;
    @Mock
    private CatalogLifecycleService catalogLifecycleService;
    @Mock
    private RestaurantCreationPort creationPort;
    @Mock
    private PrincipalOwnershipDirectory directory;
    private final RestaurantOwnershipPolicy restaurantOwnershipPolicy = new RestaurantOwnershipPolicy(false);

    private RestaurantServiceImpl restaurantService;

    private Restaurant restaurant;
    private CreateRestaurantRequest createRequest;
    @Mock
    private RestaurantMapper menuItemMapper;
    private RestaurantResponse restaurantResponse;
    @BeforeEach
    void setUp() {
        restaurantService = new RestaurantServiceImpl(restaurantRepository, menuItemMapper,
                cacheSynchronizer, searchSyncPublisher, new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                restaurantOwnershipPolicy, catalogLifecycleService,
                new DefaultCreateRestaurantUseCase(new DefaultRestaurantOwnerAssignmentUseCase(directory), creationPort));
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test Street");
        restaurant.setCreatorId(1L);

        createRequest = new CreateRestaurantRequest();
        createRequest.setName("Test Restaurant");
        createRequest.setAddress("123 Test Street");

        restaurantResponse = new RestaurantResponse();
        restaurantResponse.setId(1L);
        restaurantResponse.setName("Test Restaurant");
        restaurantResponse.setAddress("123 Test Street");
    }

    @Test
    void createRestaurant_ShouldReturnRestaurantResponse_WhenValidRequest() {
        // Given


        CreateRestaurantResult saved = creationResult();
        when(creationPort.create(any(), eq(1L))).thenReturn(saved);
        when(menuItemMapper.toResponse(saved)).thenReturn(restaurantResponse);
        // When
        RestaurantResponse response = restaurantService.createRestaurant(createRequest, 1L, RoleConstants.OWNER);

        // Then
        assertNotNull(response);
        assertEquals("Test Restaurant", response.getName());
        assertEquals("123 Test Street", response.getAddress());
        verify(creationPort).create(any(CreateRestaurantCommand.class), eq(1L));
        verifyNoInteractions(restaurantRepository, cacheSynchronizer, searchSyncPublisher, directory);
    }

    @Test
    void createRestaurant_StoresPrincipalAndLegacyCreatorWithoutChangingResponseContract() {
        CreateRestaurantResult saved = creationResult();
        when(creationPort.create(any(), eq(70L))).thenReturn(saved);
        when(menuItemMapper.toResponse(saved)).thenReturn(restaurantResponse);

        RestaurantResponse result = restaurantService.createRestaurant(
                createRequest, 70L, 7L, RoleConstants.OWNER);

        assertSame(restaurantResponse, result);
        var command = org.mockito.ArgumentCaptor.forClass(CreateRestaurantCommand.class);
        verify(creationPort).create(command.capture(), eq(70L));
        assertEquals(70L, command.getValue().actorPrincipalId());
        assertEquals(7L, command.getValue().creatorId());
        assertEquals(createRequest.getName(), command.getValue().name());
        assertEquals(createRequest.getAddress(), command.getValue().address());
        verifyNoInteractions(restaurantRepository, cacheSynchronizer, searchSyncPublisher, directory);
    }

    @Test
    void getRestaurantById_ShouldReturnRestaurant_WhenExists() {


        // Given

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(menuItemMapper.toResponse(any(Restaurant.class))).thenReturn(restaurantResponse);

        // When
        RestaurantResponse response = restaurantService.getRestaurantById(1L);

        // Then
        assertNotNull(response);
        assertEquals("Test Restaurant", response.getName());
        verify(restaurantRepository).findById(1L);
        verify(menuItemMapper).toResponse(any(Restaurant.class));
    }

    @Test
    void getRestaurantById_ShouldThrowException_WhenNotFound() {
        // Given

        when(restaurantRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () ->
                restaurantService.getRestaurantById(1L));

        verify(restaurantRepository).findById(1L);
    }

    @Test
    void getRestaurantById_ResolvesArchivedRestaurantForHistory() {
        restaurant.setLifecycleStatus(RestaurantStatus.ARCHIVED);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(menuItemMapper.toResponse(restaurant)).thenReturn(restaurantResponse);

        assertSame(restaurantResponse, restaurantService.getRestaurantById(1L));

        verify(restaurantRepository).findById(1L);
    }

    @Test
    void createRestaurant_ShouldRejectUnauthenticatedActorBeforeMapping() {
        assertThrows(OwnerAssignmentException.class, () ->
                restaurantService.createRestaurant(createRequest, null, RoleConstants.OWNER));

        verifyNoInteractions(creationPort, directory, restaurantRepository, menuItemMapper, cacheSynchronizer, searchSyncPublisher);
    }

    @Test
    void createRestaurant_ShouldRejectUnsupportedRoleBeforePersistence() {
        assertThrows(OwnerAssignmentException.class, () ->
                restaurantService.createRestaurant(createRequest, 7L, RoleConstants.CUSTOMER));

        verifyNoInteractions(creationPort, directory, restaurantRepository, menuItemMapper, cacheSynchronizer, searchSyncPublisher);
    }

    @Test
    void createRestaurant_RejectsPartialOperatingHoursBeforePersistence() {
        createRequest.setOpeningHour(LocalTime.of(18, 0));

        assertThrows(IllegalArgumentException.class, () -> restaurantService.createRestaurant(
                createRequest, 1L, 1L, RoleConstants.OWNER));

        verify(restaurantRepository, never()).save(any());
        verifyNoInteractions(creationPort, directory, cacheSynchronizer, searchSyncPublisher);
    }

    private CreateRestaurantResult creationResult() {
        return new CreateRestaurantResult(1L, "Test Restaurant", "123 Test Street", null,
                null, null, 30, null, null, null, null, 0.0, 0,
                RestaurantStatus.ACTIVE, 0L, "Asia/Ho_Chi_Minh");
    }

    @Test
    void updateRestaurant_ShouldReturnNotFoundWithoutMutation() {
        when(restaurantRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> restaurantService.updateRestaurant(
                404L, new UpdateRestaurantRequest(), 7L, 7L, RoleConstants.OWNER));

        verify(restaurantRepository, never()).saveAndFlush(any());
        verifyNoInteractions(menuItemMapper, cacheSynchronizer, searchSyncPublisher);
    }

    @Test
    void updateRestaurant_ShouldRejectForeignOwnerBeforeMappingOrPersistence() {
        restaurant.setOwnerPrincipalId(99L);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));

        assertThrows(AccessDeniedException.class, () -> restaurantService.updateRestaurant(
                1L, new UpdateRestaurantRequest(), 7L, 7L, RoleConstants.OWNER));

        verify(restaurantRepository, never()).saveAndFlush(any());
        verifyNoInteractions(menuItemMapper, cacheSynchronizer, searchSyncPublisher);
    }

    @Test
    void updateRestaurant_RejectsPartialScheduleBeforeMutatingRestaurant() {
        restaurant.setOwnerPrincipalId(1L);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        UpdateRestaurantRequest request = new UpdateRestaurantRequest();
        request.setOpeningHour(LocalTime.of(18, 0));

        assertThrows(IllegalArgumentException.class, () -> restaurantService.updateRestaurant(
                1L, request, 1L, 1L, RoleConstants.OWNER));

        assertNull(restaurant.getOpeningHour());
        assertNull(restaurant.getClosingHour());
        verify(menuItemMapper, never()).updateEntityFromDto(any(), any());
        verify(restaurantRepository, never()).saveAndFlush(any());
        verifyNoInteractions(cacheSynchronizer, searchSyncPublisher);
    }

    @Test
    void updateRestaurantAllowsChangingOneHourWhenStoredPairRemainsComplete() {
        restaurant.setOwnerPrincipalId(1L);
        restaurant.setOpeningHour(LocalTime.of(9, 0));
        restaurant.setClosingHour(LocalTime.of(18, 0));
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.saveAndFlush(restaurant)).thenReturn(restaurant);
        when(menuItemMapper.toResponse(restaurant)).thenReturn(restaurantResponse);
        UpdateRestaurantRequest request = new UpdateRestaurantRequest();
        request.setOpeningHour(LocalTime.of(10, 0));
        doAnswer(invocation -> {
            new com.delivery.restaurant_service.mapper.RestaurantMapper().updateEntityFromDto(
                    invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(menuItemMapper).updateEntityFromDto(any(), any());

        restaurantService.updateRestaurant(1L, request, 1L, 1L, RoleConstants.OWNER);

        assertEquals(LocalTime.of(10, 0), restaurant.getOpeningHour());
        assertEquals(LocalTime.of(18, 0), restaurant.getClosingHour());
        verify(menuItemMapper).updateEntityFromDto(request, restaurant);
        verify(restaurantRepository).saveAndFlush(restaurant);
    }

    @Test
    void updateRestaurant_ShouldAllowAdminAcrossOwnershipBoundary() {
        restaurant.setOwnerPrincipalId(99L);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.saveAndFlush(restaurant)).thenReturn(restaurant);
        when(menuItemMapper.toResponse(restaurant)).thenReturn(restaurantResponse);

        RestaurantResponse result = restaurantService.updateRestaurant(
                1L, new UpdateRestaurantRequest(), 7L, 7L, RoleConstants.ADMIN);

        assertSame(restaurantResponse, result);
        verify(menuItemMapper).updateEntityFromDto(any(UpdateRestaurantRequest.class), eq(restaurant));
        verify(restaurantRepository).saveAndFlush(restaurant);
    }

    @Test
    void updateRestaurant_UpdatesMutableFieldsAndSchedulesReadSideEffects() {
        restaurant.setOwnerPrincipalId(7L);
        UpdateRestaurantRequest request = new UpdateRestaurantRequest();
        request.setName("Renamed Restaurant");
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.saveAndFlush(restaurant)).thenReturn(restaurant);
        when(menuItemMapper.toResponse(restaurant)).thenReturn(restaurantResponse);

        RestaurantResponse result = restaurantService.updateRestaurant(
                1L, request, 7L, 700L, RoleConstants.OWNER);

        assertSame(restaurantResponse, result);
        verify(menuItemMapper).updateEntityFromDto(request, restaurant);
        verify(cacheSynchronizer).cacheRestaurantAfterCommit(restaurant);
        verify(searchSyncPublisher).publishRestaurantChange(restaurant, "UPDATE");
        assertEquals(7L, restaurant.getOwnerPrincipalId());
    }

    @Test
    void findByName_ShouldReturnMatchingRestaurants() {
        // Given
        List<Restaurant> restaurants = Collections.singletonList(restaurant);
        when(restaurantRepository.findByNameContainingIgnoreCaseAndLifecycleStatusNot(
                eq("Test"), eq(RestaurantStatus.ARCHIVED), any())).thenReturn(restaurants);
        when(menuItemMapper.toResponse(any(Restaurant.class))).thenReturn(restaurantResponse);
        // When
        List<RestaurantResponse> responses = restaurantService.findByName("Test");

        // Then
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("Test Restaurant", responses.get(0).getName());
        verify(restaurantRepository).findByNameContainingIgnoreCaseAndLifecycleStatusNot(
                eq("Test"), eq(RestaurantStatus.ARCHIVED), any());
        verify(menuItemMapper).toResponse(any(Restaurant.class));
    }

    @Test
    void getAllRestaurants_UsesPublicProjectionThatExcludesArchivedRows() {
        when(restaurantRepository.findByLifecycleStatusNot(
                eq(RestaurantStatus.ARCHIVED), any())).thenReturn(new PageImpl<>(List.of(restaurant)));
        when(menuItemMapper.toResponse(restaurant)).thenReturn(restaurantResponse);

        List<RestaurantResponse> responses = restaurantService.getAllRestaurants();

        assertEquals(List.of(restaurantResponse), responses);
        verify(restaurantRepository).findByLifecycleStatusNot(
                eq(RestaurantStatus.ARCHIVED), any());
    }

    @Test
    void getAllManagedRestaurants_IncludesArchivedRows() {
        restaurant.setLifecycleStatus(RestaurantStatus.ARCHIVED);
        when(restaurantRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(restaurant)));
        when(menuItemMapper.toResponse(restaurant)).thenReturn(restaurantResponse);

        assertEquals(List.of(restaurantResponse), restaurantService.getAllManagedRestaurants());
    }

    @Test
    void getAllRestaurantsPage_UsesPublicArchivedFilterAndKeyword() {
        when(restaurantRepository.findPageByNameContainingIgnoreCaseAndLifecycleStatusNot(
                eq("pizza"), eq(RestaurantStatus.ARCHIVED), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(restaurant)));
        when(menuItemMapper.toResponse(restaurant)).thenReturn(restaurantResponse);

        var page = restaurantService.getAllRestaurantsPage(1, 20, "  pizza  ");

        assertEquals(1, page.getTotalElements());
        verify(restaurantRepository).findPageByNameContainingIgnoreCaseAndLifecycleStatusNot(
                eq("pizza"), eq(RestaurantStatus.ARCHIVED), eq(PageRequest.of(1, 20)));
    }

    @Test
    void getAllRestaurantsPage_UsesPublicArchivedFilterWithoutKeyword() {
        when(restaurantRepository.findByLifecycleStatusNot(
                eq(RestaurantStatus.ARCHIVED), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(restaurant)));
        when(menuItemMapper.toResponse(restaurant)).thenReturn(restaurantResponse);

        var page = restaurantService.getAllRestaurantsPage(0, 24, "  ");

        assertEquals(1, page.getTotalElements());
        verify(restaurantRepository).findByLifecycleStatusNot(
                eq(RestaurantStatus.ARCHIVED), eq(PageRequest.of(0, 24)));
    }

    @Test
    void deleteRestaurant_ShouldArchiveWithoutPhysicalDeletion_WhenUserIsOwner() {
        // Given
        // When
        restaurantService.deleteRestaurant(1L, 1L, RoleConstants.OWNER);

        // Then
        verify(catalogLifecycleService).archiveRestaurant(1L, 1L, 1L, RoleConstants.OWNER);
    }

    @Test
    void deleteRestaurant_DelegatesOwnershipAndLifecycleToCatalogBoundary() {
        restaurantService.deleteRestaurant(1L, 1L, RoleConstants.OWNER);

        verify(catalogLifecycleService).archiveRestaurant(1L, 1L, 1L, RoleConstants.OWNER);
        verifyNoInteractions(restaurantRepository);
    }

    @Test
    void deleteRestaurant_ShouldAllowAdmin_WhenAdminIsNotOwner() {
        restaurant.setCreatorId(2L);
        restaurantService.deleteRestaurant(1L, 99L, RoleConstants.ADMIN);

        verify(catalogLifecycleService).archiveRestaurant(1L, 99L, 99L, RoleConstants.ADMIN);
    }

    @Test
    void deleteRestaurant_PassesMissingRoleToCatalogBoundary() {
        restaurantService.deleteRestaurant(1L, 1L, null);

        verify(catalogLifecycleService).archiveRestaurant(1L, 1L, 1L, null);
        verifyNoInteractions(restaurantRepository);
    }

    @Test
    void deleteRestaurant_IsIdempotentWhenAlreadyArchived() {
        restaurant.setLifecycleStatus(RestaurantStatus.ARCHIVED);
        restaurantService.deleteRestaurant(1L, 1L, RoleConstants.OWNER);

        verify(catalogLifecycleService).archiveRestaurant(1L, 1L, 1L, RoleConstants.OWNER);
    }
}
