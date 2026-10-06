package com.delivery.search_service.repository;

import com.delivery.search_service.document.DishDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface DishSearchRepository extends ElasticsearchRepository<DishDocument, String> {
    Page<DishDocument> findByNameOrDescription(String name, String description, Pageable pageable);
}
