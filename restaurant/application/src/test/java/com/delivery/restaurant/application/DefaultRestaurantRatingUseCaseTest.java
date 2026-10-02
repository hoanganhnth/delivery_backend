package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.rating.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DefaultRestaurantRatingUseCaseTest {
    private static final SubmitRestaurantRatingCommand COMMAND = new SubmitRestaurantRatingCommand(7L, 42L, 99L, 5, "Great");

    @Test void submissionKeepsEligibilityLockInsertAndAggregateInOneTransaction() {
        var f = new Fixture();
        assertThat(f.core().submitRating(COMMAND).orderId()).isEqualTo(99L);
        assertThat(f.calls).containsExactly("begin", "eligible:99:42:7", "lock:7", "restaurant:7",
                "rated:99", "insert", "aggregate:APPROVED", "projection:4.5:101", "commit");
    }

    @Test void missingRestaurantAndDuplicateOrderRejectBeforeInsert() {
        var missing = new Fixture(); missing.restaurant = false;
        assertThatThrownBy(() -> missing.core().submitRating(COMMAND)).hasMessage("Restaurant not found with ID: 7");
        assertThat(missing.calls).doesNotContain("insert");
        var duplicate = new Fixture(); duplicate.duplicate = true;
        assertThatThrownBy(() -> duplicate.core().submitRating(COMMAND))
                .isInstanceOf(RestaurantRatingConflictException.class).hasMessage(DefaultRestaurantRatingUseCase.DUPLICATE_MESSAGE);
        assertThat(duplicate.calls).doesNotContain("insert");
    }

    @Test void eligibilityFailureDoesNotAcquireLock() {
        var f = new Fixture(); f.eligible = false;
        assertThatThrownBy(() -> f.core().submitRating(COMMAND)).hasMessage("Order is not delivered");
        assertThat(f.calls).containsExactly("begin", "eligible:99:42:7");
    }

    @Test void moderationLocksBeforeSavingAndRefreshesApprovedAggregate() {
        var f = new Fixture();
        assertThat(f.core().updateRatingStatus(21L, "approved").status()).isEqualTo("APPROVED");
        assertThat(f.calls).containsExactly("begin", "find:21", "lock:7", "status:APPROVED", "restaurant:7",
                "aggregate:APPROVED", "projection:4.5:101", "commit");
    }

    @Test void moderationRejectsMissingRatingInvalidStatusAndMissingRestaurant() {
        var missing = new Fixture(); missing.rating = false;
        assertThatThrownBy(() -> missing.core().updateRatingStatus(21L, "APPROVED"))
                .hasMessage("Rating not found with ID: 21");
        var invalid = new Fixture();
        assertThatThrownBy(() -> invalid.core().updateRatingStatus(21L, "invalid")).isInstanceOf(IllegalArgumentException.class);
        assertThat(invalid.calls).doesNotContain("status:APPROVED");
        var restaurant = new Fixture(); restaurant.restaurant = false;
        assertThatThrownBy(() -> restaurant.core().updateRatingStatus(21L, "REJECTED")).hasMessage("Restaurant not found");
        assertThat(restaurant.calls).doesNotContain("aggregate:APPROVED");
    }

    @Test void publicAndCompatibilityReadsRetainApprovalFilterAndHundredItemBound() {
        var f = new Fixture(); var c = f.core();
        c.getRestaurantRatings(7L); c.getMyRatings(42L); c.getAllRatings();
        c.getRestaurantRatingsPage(7L, 2, 10); c.getAllRatingsPage(3, 20);
        assertThat(f.calls).containsExactly("public:APPROVED:100", "customer:42:100", "all:100",
                "publicPage:APPROVED:2:10", "allPage:3:20");
    }

    private static final class Fixture implements RestaurantRatingStorePort, RatingOrderEligibilityPort, RestaurantTransactionPort {
        final List<String> calls = new ArrayList<>();
        boolean restaurant = true, rating = true, duplicate, eligible = true;
        DefaultRestaurantRatingUseCase core() { return new DefaultRestaurantRatingUseCase(this, this, this); }
        public <T> T required(Supplier<T> action) { calls.add("begin"); T result = action.get(); calls.add("commit"); return result; }
        public void requireDeliveredOrder(Long order, Long customer, Long restaurant) {
            calls.add("eligible:" + order + ":" + customer + ":" + restaurant);
            if (!eligible) throw new IllegalArgumentException("Order is not delivered");
        }
        public void lockRestaurant(Long id) { calls.add("lock:" + id); }
        public boolean restaurantExists(Long id) { calls.add("restaurant:" + id); return restaurant; }
        public boolean orderRated(Long id) { calls.add("rated:" + id); return duplicate; }
        public RestaurantRatingResult insert(SubmitRestaurantRatingCommand c) { calls.add("insert"); return result("PENDING"); }
        public Optional<RestaurantRatingResult> find(Long id) { calls.add("find:" + id); return rating ? Optional.of(result("PENDING")) : Optional.empty(); }
        public RestaurantRatingResult updateStatus(Long id, RestaurantRatingStatus status) { calls.add("status:" + status); return result(status.name()); }
        public Aggregate aggregate(Long id, RestaurantRatingStatus status) { calls.add("aggregate:" + status); return new Aggregate(101, 4.5); }
        public void updateRestaurantAggregate(Long id, double average, int count) { calls.add("projection:" + average + ":" + count); }
        public List<RestaurantRatingResult> byRestaurant(Long id, RestaurantRatingStatus status, int limit) { calls.add("public:" + status + ":" + limit); return List.of(); }
        public List<RestaurantRatingResult> byCustomer(Long id, int limit) { calls.add("customer:" + id + ":" + limit); return List.of(); }
        public List<RestaurantRatingResult> all(int limit) { calls.add("all:" + limit); return List.of(); }
        public RestaurantRatingPage byRestaurantPage(Long id, RestaurantRatingStatus status, int page, int size) { calls.add("publicPage:" + status + ":" + page + ":" + size); return null; }
        public RestaurantRatingPage allPage(int page, int size) { calls.add("allPage:" + page + ":" + size); return null; }
        private RestaurantRatingResult result(String status) { return new RestaurantRatingResult(21L, 7L, 42L, 99L, 5, "Great", status, null); }
    }
}
