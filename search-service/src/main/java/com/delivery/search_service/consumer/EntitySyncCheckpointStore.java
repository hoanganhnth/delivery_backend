package com.delivery.search_service.consumer;

import com.delivery.search_service.dto.EntitySyncEvent;

/**
 * Atomically claims the latest projection version for one entity before its
 * search document is changed. This is deliberately separate from the document
 * write: Elasticsearch only makes one document atomic at a time.
 */
public interface EntitySyncCheckpointStore {

    ClaimResult claim(EntitySyncEvent event, String payloadFingerprint);

    enum ClaimResult {
        APPLY,
        EXACT_REPLAY,
        STALE
    }
}
