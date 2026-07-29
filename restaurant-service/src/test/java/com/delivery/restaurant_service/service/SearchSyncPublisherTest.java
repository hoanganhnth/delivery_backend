package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.entity.RestaurantOutboxEvent;
import com.delivery.restaurant_service.repository.RestaurantOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SearchSyncPublisherTest {

    @Test
    void storesStableSearchEventInExistingTransactionalOutbox() throws Exception {
        RestaurantOutboxEventRepository repository = mock(RestaurantOutboxEventRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SearchSyncPublisher publisher = new SearchSyncPublisher(repository, objectMapper);
        ReflectionTestUtils.setField(publisher, "enabled", true);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(7L);
        restaurant.setName("R");

        publisher.publishRestaurantChange(restaurant, "UPDATE");

        ArgumentCaptor<RestaurantOutboxEvent> event =
                ArgumentCaptor.forClass(RestaurantOutboxEvent.class);
        verify(repository).save(event.capture());
        assertThat(event.getValue().getTopic()).isEqualTo("entity-sync");
        assertThat(event.getValue().getEventKey()).isEqualTo("7");
        assertThat(event.getValue().getStatus()).isEqualTo(RestaurantOutboxEvent.Status.PENDING);
        assertThat(event.getValue().getEventId()).isNotNull();
        var payload = objectMapper.readTree(event.getValue().getPayload());
        assertThat(payload.path("eventId").asText()).isEqualTo(event.getValue().getEventId().toString());
        assertThat(payload.hasNonNull("occurredAt")).isTrue();
    }
}
