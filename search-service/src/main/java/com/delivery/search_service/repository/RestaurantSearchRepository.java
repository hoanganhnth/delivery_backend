package com.delivery.search_service.repository;

import com.delivery.search_service.document.RestaurantDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface RestaurantSearchRepository extends ElasticsearchRepository<RestaurantDocument, String> {
    Page<RestaurantDocument> findByNameOrDescription(String name, String description, Pageable pageable);
}
