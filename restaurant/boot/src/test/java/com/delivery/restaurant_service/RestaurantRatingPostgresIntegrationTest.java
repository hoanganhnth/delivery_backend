package com.delivery.restaurant_service;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.application.api.RatingOrderEligibilityPort;
import com.delivery.restaurant.domain.rating.RestaurantRatingConflictException;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.repository.RestaurantRatingRepository;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true", "spring.kafka.listener.auto-startup=false",
        "app.outbox.relay-enabled=false", "app.search-sync.enabled=false", "order.service.url=http://order-service"
})
class RestaurantRatingPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void fixture(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired RestaurantRatingUseCase ratings;
    @Autowired RestaurantRatingRepository ratingRows;
    @Autowired org.springframework.jdbc.core.JdbcTemplate sql;
    @MockitoSpyBean RestaurantRepository restaurants;
    @MockitoBean RatingOrderEligibilityPort eligibility;

    @Test void moderationRecalculatesOnlyApprovedRatingsAndSupportsEmptyAggregate() {
        Long id = restaurant("rating-aggregate");
        var first = ratings.submitRating(command(id, 99001L, 5));
        var second = ratings.submitRating(command(id, 99002L, 3));
        assertThat(restaurants.findById(id).orElseThrow().getRatingCount()).isZero();
        ratings.updateRatingStatus(first.id(), "approved");
        ratings.updateRatingStatus(second.id(), "APPROVED");
        assertThat(restaurants.findById(id).orElseThrow().getRating()).isEqualTo(4.0);
        assertThat(restaurants.findById(id).orElseThrow().getRatingCount()).isEqualTo(2);
        ratings.updateRatingStatus(first.id(), "REJECTED");
        ratings.updateRatingStatus(second.id(), "REJECTED");
        assertThat(restaurants.findById(id).orElseThrow().getRating()).isZero();
        assertThat(restaurants.findById(id).orElseThrow().getRatingCount()).isZero();
    }

    @Test void simultaneousDuplicateSubmissionsCommitOneRating() throws Exception {
        Long id = restaurant("rating-concurrency");
        var start = new CountDownLatch(1);
        var wins = new AtomicInteger(); var conflicts = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> submit = () -> {
                start.await(10, TimeUnit.SECONDS);
                try { ratings.submitRating(command(id, 99003L, 5)); wins.incrementAndGet(); }
                catch (RestaurantRatingConflictException expected) { conflicts.incrementAndGet(); }
                return null;
            };
            var first = pool.submit(submit); var second = pool.submit(submit); start.countDown();
            first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
            assertThat(wins.get()).isEqualTo(1); assertThat(conflicts.get()).isEqualTo(1);
            assertThat(ratingRows.findAll().stream().filter(row -> row.getOrderId().equals(99003L))).hasSize(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void aggregateWriteFailureRollsBackInsertedRating() {
        Long id = restaurant("rating-rollback");
        doThrow(new IllegalStateException("fixture aggregate failure")).when(restaurants).save(any(Restaurant.class));
        try {
            assertThatThrownBy(() -> ratings.submitRating(command(id, 99004L, 5)))
                    .hasMessage("fixture aggregate failure");
            assertThat(ratingRows.existsByOrderId(99004L)).isFalse();
        } finally { reset(restaurants); }
    }

    @Test void advisoryLockSupportsRestaurantIdentityBeyondInt32() {
        Long original = restaurant("rating-large-identity");
        Long id = 5_000_000_000L;
        sql.update("update restaurant set id=? where id=?", id, original);
        assertThat(ratings.submitRating(command(id, 99005L, 5)).restaurantId()).isEqualTo(id);
    }

    private Long restaurant(String name) {
        var restaurant = new Restaurant(); restaurant.setName(name); restaurant.setAddress("Fixture Street");
        restaurant.setCreatorId(70L);
        return restaurants.saveAndFlush(restaurant).getId();
    }
    private SubmitRestaurantRatingCommand command(Long id, Long orderId, int score) {
        return new SubmitRestaurantRatingCommand(id, 42L, orderId, score, "Fixture");
    }
}
