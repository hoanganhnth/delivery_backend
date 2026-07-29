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

    private static SearchService service(ObjectProvider<RestaurantSearchRepository> restaurants) {
        ObjectProvider<DishSearchRepository> dishes = mock(ObjectProvider.class);
        return new SearchService(restaurants, dishes);
    }
}
