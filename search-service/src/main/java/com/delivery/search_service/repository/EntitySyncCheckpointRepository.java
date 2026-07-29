package com.delivery.search_service.repository;

import com.delivery.search_service.document.EntitySyncCheckpoint;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface EntitySyncCheckpointRepository
        extends ElasticsearchRepository<EntitySyncCheckpoint, String> {
}
