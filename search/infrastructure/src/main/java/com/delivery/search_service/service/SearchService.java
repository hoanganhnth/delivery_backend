package com.delivery.search_service.service;

import com.delivery.search.application.DefaultSearchQueryUseCase;
import com.delivery.search.application.api.SearchQuery;
import com.delivery.search.application.api.SearchQueryUseCase;
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
        return execute(query, new DefaultSearchQueryUseCase<>(() -> {
            RestaurantSearchRepository repository = restaurantRepository.getIfAvailable();
            return repository == null ? null
                    : input -> repository.findByNameOrDescription(input.text(), input.text(), pageable);
        }, "Restaurant", failure -> log.error("Restaurant search failed", failure)));
    }

    public Page<DishDocument> searchDishes(String query, Pageable pageable) {
        return execute(query, new DefaultSearchQueryUseCase<>(() -> {
            DishSearchRepository repository = dishRepository.getIfAvailable();
            return repository == null ? null
                    : input -> repository.findByNameOrDescription(input.text(), input.text(), pageable);
        }, "Dish", failure -> log.error("Dish search failed", failure)));
    }

    private <R> R execute(String query, SearchQueryUseCase<R> useCase) {
        try {
            return useCase.search(new SearchQuery(query));
        } catch (com.delivery.search.application.api.SearchUnavailableException exception) {
            throw new SearchUnavailableException(exception.getMessage(), exception.getCause());
        }
    }
}
