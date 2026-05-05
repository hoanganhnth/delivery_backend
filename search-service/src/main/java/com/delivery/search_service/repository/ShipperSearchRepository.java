package com.delivery.search_service.repository;

import com.delivery.search_service.document.ShipperDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ShipperSearchRepository extends ElasticsearchRepository<ShipperDocument, String> {
    Page<ShipperDocument> findByName(String name, Pageable pageable);
}
