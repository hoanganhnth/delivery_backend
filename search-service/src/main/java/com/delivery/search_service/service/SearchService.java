package com.delivery.search_service.service;

import com.delivery.search_service.document.DishDocument;
import com.delivery.search_service.document.RestaurantDocument;
import com.delivery.search_service.repository.DishSearchRepository;
import com.delivery.search_service.repository.RestaurantSearchRepository;
import com.delivery.search_service.exception.SearchUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ObjectProvider<RestaurantSearchRepository> restaurantRepository;
    private final ObjectProvider<DishSearchRepository> dishRepository;
    public Page<RestaurantDocument> searchRestaurants(String query, Pageable pageable) {
        RestaurantSearchRepository repository = restaurantRepository.getIfAvailable();
        if (repository == null) {
            throw new SearchUnavailableException("Restaurant search repository is unavailable");
        }
        try {
            return repository.findByNameOrDescription(query, query, pageable);
        } catch (RuntimeException exception) {
            log.error("Restaurant search failed", exception);
            throw new SearchUnavailableException("Restaurant search failed", exception);
        }
    }

    public Page<DishDocument> searchDishes(String query, Pageable pageable) {
        DishSearchRepository repository = dishRepository.getIfAvailable();
        if (repository == null) {
            throw new SearchUnavailableException("Dish search repository is unavailable");
        }
        try {
            return repository.findByNameOrDescription(query, query, pageable);
        } catch (RuntimeException exception) {
            log.error("Dish search failed", exception);
            throw new SearchUnavailableException("Dish search failed", exception);
        }
    }

}
