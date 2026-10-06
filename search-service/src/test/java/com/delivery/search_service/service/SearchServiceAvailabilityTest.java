package com.delivery.search_service.service;

import com.delivery.search_service.document.RestaurantDocument;
import com.delivery.search_service.exception.SearchUnavailableException;
import com.delivery.search_service.repository.DishSearchRepository;
import com.delivery.search_service.repository.RestaurantSearchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchServiceAvailabilityTest {

    @Test
    void missingRepositoryIsReportedAsUnavailable() {
        ObjectProvider<RestaurantSearchRepository> restaurants = mock(ObjectProvider.class);
        SearchService service = service(restaurants);

        assertThrows(SearchUnavailableException.class,
                () -> service.searchRestaurants("pho", PageRequest.of(0, 20)));
    }

    @Test
    void repositoryFailureIsReportedAsUnavailable() {
        ObjectProvider<RestaurantSearchRepository> restaurants = mock(ObjectProvider.class);
        RestaurantSearchRepository repository = mock(RestaurantSearchRepository.class);
        when(restaurants.getIfAvailable()).thenReturn(repository);
        when(repository.findByNameOrDescription("pho", "pho", PageRequest.of(0, 20)))
                .thenThrow(new IllegalStateException("cluster endpoint leaked"));
        SearchService service = service(restaurants);

        assertThrows(SearchUnavailableException.class,
                () -> service.searchRestaurants("pho", PageRequest.of(0, 20)));
    }

    @Test
    void successfulRepositoryResultIsReturnedUnchanged() {
        ObjectProvider<RestaurantSearchRepository> restaurants = mock(ObjectProvider.class);
        RestaurantSearchRepository repository = mock(RestaurantSearchRepository.class);
        PageRequest pageable = PageRequest.of(0, 20);
        Page<RestaurantDocument> result = new PageImpl<>(List.of(new RestaurantDocument()));
        when(restaurants.getIfAvailable()).thenReturn(repository);
        when(repository.findByNameOrDescription("pho", "pho", pageable)).thenReturn(result);
        SearchService service = service(restaurants);

        assertSame(result, service.searchRestaurants("pho", pageable));
    }


    @Test
    void dishDelegationPreservesPageableSortIdentityAndFailureCause() {
        ObjectProvider<RestaurantSearchRepository> restaurants = mock(ObjectProvider.class);
        ObjectProvider<DishSearchRepository> dishes = mock(ObjectProvider.class);
        var service = new SearchService(restaurants, dishes);
        var pageable = PageRequest.of(3, 7, org.springframework.data.domain.Sort.by("name"));
        var unavailable = assertThrows(SearchUnavailableException.class, () -> service.searchDishes(" q ", pageable));
        org.junit.jupiter.api.Assertions.assertEquals("Dish search repository is unavailable", unavailable.getMessage());
        org.junit.jupiter.api.Assertions.assertNull(unavailable.getCause());
        var repository = mock(DishSearchRepository.class);
        when(dishes.getIfAvailable()).thenReturn(repository);
        Page<com.delivery.search_service.document.DishDocument> result = new PageImpl<>(List.of(), pageable, 27);
        when(repository.findByNameOrDescription(" q ", " q ", pageable)).thenReturn(result);
        assertSame(result, service.searchDishes(" q ", pageable));
        RuntimeException failure = new IllegalStateException("backend details");
        when(repository.findByNameOrDescription(" q ", " q ", pageable)).thenThrow(failure);
        var wrapped = assertThrows(SearchUnavailableException.class, () -> service.searchDishes(" q ", pageable));
        org.junit.jupiter.api.Assertions.assertEquals("Dish search failed", wrapped.getMessage());
        assertSame(failure, wrapped.getCause());
    }

    @Test
    void providerResolutionFailureRemainsOutsideUnavailableTranslation() {
        ObjectProvider<RestaurantSearchRepository> restaurants = mock(ObjectProvider.class);
        RuntimeException failure = new IllegalStateException("bean resolution failure");
        when(restaurants.getIfAvailable()).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> service(restaurants).searchRestaurants("q", PageRequest.of(0, 20))));
    }

    private static SearchService service(ObjectProvider<RestaurantSearchRepository> restaurants) {
        ObjectProvider<DishSearchRepository> dishes = mock(ObjectProvider.class);
        return new SearchService(restaurants, dishes);
    }
}
