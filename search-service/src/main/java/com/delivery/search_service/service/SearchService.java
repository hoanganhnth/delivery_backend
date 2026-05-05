package com.delivery.search_service.service;

import com.delivery.search_service.document.DishDocument;
import com.delivery.search_service.document.RestaurantDocument;
import com.delivery.search_service.document.ShipperDocument;
import com.delivery.search_service.repository.DishSearchRepository;
import com.delivery.search_service.repository.RestaurantSearchRepository;
import com.delivery.search_service.repository.ShipperSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final RestaurantSearchRepository restaurantRepository;
    private final DishSearchRepository dishRepository;
    private final ShipperSearchRepository shipperRepository;
    private final SearchCacheService cacheService;

    @SuppressWarnings("unchecked")
    public Page<RestaurantDocument> searchRestaurants(String query, Pageable pageable) {
        String cacheKey = "search:restaurant:" + query.toLowerCase() + ":" + pageable.getPageNumber() + ":" + pageable.getPageSize();
        
        Optional<Object> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            return (Page<RestaurantDocument>) cached.get();
        }

        Page<RestaurantDocument> results = restaurantRepository.findByNameOrDescription(query, query, pageable);
        cacheService.put(cacheKey, results);
        return results;
    }

    @SuppressWarnings("unchecked")
    public Page<DishDocument> searchDishes(String query, Pageable pageable) {
        String cacheKey = "search:dish:" + query.toLowerCase() + ":" + pageable.getPageNumber() + ":" + pageable.getPageSize();
        
        Optional<Object> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            return (Page<DishDocument>) cached.get();
        }

        Page<DishDocument> results = dishRepository.findByNameOrDescription(query, query, pageable);
        cacheService.put(cacheKey, results);
        return results;
    }

    @SuppressWarnings("unchecked")
    public Page<ShipperDocument> searchShippers(String query, Pageable pageable) {
        String cacheKey = "search:shipper:" + query.toLowerCase() + ":" + pageable.getPageNumber() + ":" + pageable.getPageSize();
        
        Optional<Object> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            return (Page<ShipperDocument>) cached.get();
        }

        Page<ShipperDocument> results = shipperRepository.findByName(query, pageable);
        cacheService.put(cacheKey, results);
        return results;
    }
}
