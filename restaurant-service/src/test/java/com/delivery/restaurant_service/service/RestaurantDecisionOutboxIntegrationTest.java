package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.client.OrderDecisionEligibilityClient;
import com.delivery.restaurant_service.exception.RestaurantDecisionConflictException;
import com.delivery.restaurant_service.repository.RestaurantOrderDecisionRepository;
import com.delivery.restaurant_service.repository.RestaurantOutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:restaurant_decision;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "app.outbox.relay-enabled=false",
        "app.kafka.topics.order-confirmed=b8.restaurant.confirmed",
        "app.kafka.topics.order-rejected=b8.restaurant.rejected",
        "order.service.url=http://order-service"
})
class RestaurantDecisionOutboxIntegrationTest {

    @Autowired RestaurantOrderEventPublisher publisher;
    @Autowired RestaurantOrderDecisionRepository decisionRepository;
    @Autowired RestaurantOutboxEventRepository outboxRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @MockBean OrderDecisionEligibilityClient orderEligibilityClient;

    @BeforeEach
    void clean() {
        outboxRepository.deleteAll();
        decisionRepository.deleteAll();
    }

    @Test
    void duplicateConfirmationIsIdempotent() {
        publisher.publishConfirmed(101L, 7L, 70L, 20, null);
        publisher.publishConfirmed(101L, 7L, 70L, 20, null);

        assertThat(decisionRepository.count()).isEqualTo(1);
        assertThat(decisionRepository.findById(101L).orElseThrow().getPayloadFingerprint())
                .hasSize(64);
        assertThat(outboxRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.findAll().get(0).getPayload()).contains("eventId");
        assertThat(outboxRepository.findAll().get(0).getTopic())
                .isEqualTo("b8.restaurant.confirmed");
    }

    @Test
    void storedDecisionFingerprintRejectsContradictoryReplayEvenIfOutboxIsPruned() {
        publisher.publishConfirmed(101L, 7L, 70L, 20, null);
        outboxRepository.deleteAll();

        assertThatThrownBy(() -> publisher.publishConfirmed(101L, 7L, 70L, 30, "changed payload"))
                .isInstanceOf(RestaurantDecisionConflictException.class)
                .hasMessageContaining("contradictory payload");
        assertThat(decisionRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void storedDecisionFingerprintKeepsExactReplayIdempotentEvenIfOutboxIsPruned() {
        publisher.publishConfirmed(101L, 7L, 70L, 20, null);
        outboxRepository.deleteAll();

        publisher.publishConfirmed(101L, 7L, 70L, 20, null);

        assertThat(decisionRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void contradictoryConfirmationReplayIsRejectedWithoutAnotherEvent() {
        publisher.publishConfirmed(101L, 7L, 70L, 20, null);

        assertThatThrownBy(() -> publisher.publishConfirmed(101L, 7L, 70L, 30, "changed payload"))
                .isInstanceOf(RestaurantDecisionConflictException.class)
                .hasMessageContaining("contradictory payload");
        assertThat(decisionRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void oppositeDecisionIsRejectedWithoutAnotherEvent() {
        publisher.publishConfirmed(101L, 7L, 70L, 20, null);

        assertThatThrownBy(() -> publisher.publishRejected(101L, 7L, 70L, "closed"))
                .isInstanceOf(RestaurantDecisionConflictException.class);
        assertThat(decisionRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void rollbackRemovesDecisionAndEvent() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            publisher.publishRejected(202L, 8L, 80L, "closed");
            throw new DeliberateRollback();
        })).isInstanceOf(DeliberateRollback.class);

        assertThat(decisionRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void ineligibleOrderIsRejectedBeforeAnythingIsStored() {
        doThrow(new IllegalArgumentException("wrong restaurant"))
                .when(orderEligibilityClient).requirePendingOrderForRestaurant(303L, 9L);

        assertThatThrownBy(() -> publisher.publishConfirmed(303L, 9L, 90L, 20, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wrong restaurant");
        assertThat(decisionRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void invalidDecisionInputFailsClosedBeforeEligibilityOrPersistence() {
        assertThatThrownBy(() -> publisher.publishConfirmed(0L, 7L, 70L, 20, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId");
        assertThatThrownBy(() -> publisher.publishRejected(101L, 0L, 70L, "closed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("restaurantId");
        assertThatThrownBy(() -> publisher.publishRejected(101L, 7L, 70L, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejectionReason");

        verifyNoInteractions(orderEligibilityClient);
        assertThat(decisionRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    private static final class DeliberateRollback extends RuntimeException {
    }
}
