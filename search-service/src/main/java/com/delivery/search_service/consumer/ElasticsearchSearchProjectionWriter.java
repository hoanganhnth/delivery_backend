package com.delivery.search_service.consumer;

import com.delivery.search_service.dto.EntitySyncEvent;
import com.delivery.search_service.document.DishDocument;
import com.delivery.search_service.document.RestaurantDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * Writes projection documents with Elasticsearch external_gte versioning. The
 * version is the producer's occurredAt, so an older Kafka partition can never
 * overwrite a newer projection even after both checkpoint claims succeeded.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchSearchProjectionWriter implements SearchProjectionWriter {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Override
    public void apply(EntitySyncEvent event) {
        String index = switch (event.getEntityType().toUpperCase(Locale.ROOT)) {
            case "RESTAURANT" -> "restaurant";
            case "DISH" -> "dish";
            default -> throw new IllegalArgumentException("Unsupported entity type: " + event.getEntityType());
        };
        long version;
        try {
            version = Math.addExact(
                    Math.multiplyExact(event.getOccurredAt().toEpochSecond(ZoneOffset.UTC), 1_000_000_000L),
                    event.getOccurredAt().getNano());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("entity-sync occurredAt cannot be represented as a version", exception);
        }
        if (version <= 0) {
            throw new IllegalArgumentException("entity-sync occurredAt must be after the Unix epoch");
        }

        String path = "/" + index + "/_doc/" + encodePathSegment(event.getEntityId());
        Request request = "DELETE".equalsIgnoreCase(event.getAction())
                ? new Request("DELETE", path)
                : new Request("PUT", path);
        request.addParameter("version", Long.toString(version));
        request.addParameter("version_type", "external_gte");
        if (!"DELETE".equalsIgnoreCase(event.getAction())) {
            try {
                request.setEntity(new StringEntity(objectMapper.writeValueAsString(documentPayload(event)),
                        ContentType.APPLICATION_JSON));
            } catch (IOException exception) {
                throw new IllegalArgumentException("entity-sync payload cannot be serialized", exception);
            }
        }
        try {
            restClient.performRequest(request);
        } catch (ResponseException exception) {
            int status = exception.getResponse().getStatusLine().getStatusCode();
            // A newer document already won the race, or an exact tombstone was
            // replayed after its delete. In both cases the desired monotonic
            // projection has already been reached.
            if (status == 404 || status == 409) {
                return;
            }
            throw new IllegalStateException("Elasticsearch projection write failed", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Elasticsearch projection is unavailable", exception);
        }
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Preserve the previous repository writer's DTO conversion contract: a
     * malformed source payload must fail Kafka processing rather than becoming
     * an arbitrary Elasticsearch document, and the payload ID cannot disagree
     * with the Kafka entity identity.
     */
    private Object documentPayload(EntitySyncEvent event) {
        return switch (event.getEntityType().toUpperCase(Locale.ROOT)) {
            case "RESTAURANT" -> {
                RestaurantDocument document = objectMapper.convertValue(event.getPayload(), RestaurantDocument.class);
                document.setId(event.getEntityId());
                yield document;
            }
            case "DISH" -> {
                DishDocument document = objectMapper.convertValue(event.getPayload(), DishDocument.class);
                document.setId(event.getEntityId());
                yield document;
            }
            default -> throw new IllegalArgumentException("Unsupported entity type: " + event.getEntityType());
        };
    }
}
