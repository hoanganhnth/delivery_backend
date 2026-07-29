package com.delivery.search_service.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

/** Monotonic fence that prevents a replayed/DLT event from restoring stale search data. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "entity_sync_checkpoint")
public class EntitySyncCheckpoint {
    @Id
    private String id;
    @Field(type = FieldType.Keyword)
    private String eventId;
    @Field(type = FieldType.Date)
    private LocalDateTime occurredAt;
    @Field(type = FieldType.Keyword)
    private String action;
    @Field(type = FieldType.Keyword)
    private String payloadFingerprint;
}
