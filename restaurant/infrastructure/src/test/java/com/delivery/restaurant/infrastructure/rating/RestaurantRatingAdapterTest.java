package com.delivery.restaurant.infrastructure.rating;

import com.delivery.restaurant.application.api.RestaurantRatingResult;
import com.delivery.restaurant.application.api.RestaurantRatingUseCase;
import com.delivery.restaurant.application.api.RestaurantTransactionPort;
import com.delivery.restaurant.application.DefaultRestaurantRatingUseCase;
import com.delivery.restaurant.domain.rating.RestaurantRatingConflictException;
import com.delivery.restaurant.application.api.SubmitRestaurantRatingCommand;
import com.delivery.restaurant.application.api.RatingOrderEligibilityPort;
import com.delivery.restaurant_service.entity.RatingStatus;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.entity.RestaurantRating;
import com.delivery.restaurant_service.repository.RestaurantRatingRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestaurantRatingAdapterTest {

    @Test
    void submissionUsesExplicitAggregateLockAndDatabaseAggregate() {
        RestaurantRatingRepository ratings = mock(RestaurantRatingRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        RatingOrderEligibilityPort eligibility = mock(RatingOrderEligibilityPort.class);
        RestaurantRatingLock ratingLock = mock(RestaurantRatingLock.class);
        Restaurant restaurant = restaurant(7L);
        when(restaurants.findById(7L)).thenReturn(Optional.of(restaurant));
        when(ratings.existsByOrderId(99L)).thenReturn(false);
        when(ratings.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ratings.aggregateByRestaurantAndStatus(7L, RatingStatus.APPROVED))
                .thenReturn(new RestaurantRatingAggregate(101, 4.5));

        RestaurantRatingResult result = service(ratings, restaurants, eligibility, ratingLock)
                .submitRating(new SubmitRestaurantRatingCommand(7L, 42L, 99L, 5, "Great"));

        assertThat(result.restaurantId()).isEqualTo(7L);
        assertThat(restaurant.getRating()).isEqualTo(4.5);
        assertThat(restaurant.getRatingCount()).isEqualTo(101);
        InOrder order = inOrder(ratingLock, restaurants, ratings);
        order.verify(ratingLock).lock(7L);
        order.verify(restaurants).findById(7L);
        order.verify(ratings).existsByOrderId(99L);
        order.verify(ratings).saveAndFlush(any(RestaurantRating.class));
        order.verify(ratings).aggregateByRestaurantAndStatus(7L, RatingStatus.APPROVED);
        order.verify(restaurants).save(restaurant);
        verify(restaurants, never()).findByIdForUpdate(7L);
    }

    @Test
    void duplicateRatingConstraintIsReportedAsConflict() {
        RestaurantRatingRepository ratings = mock(RestaurantRatingRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        RatingOrderEligibilityPort eligibility = mock(RatingOrderEligibilityPort.class);
        RestaurantRatingLock ratingLock = mock(RestaurantRatingLock.class);
        when(restaurants.findById(7L)).thenReturn(Optional.of(restaurant(7L)));
        when(ratings.existsByOrderId(99L)).thenReturn(false);
        when(ratings.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_restaurant_ratings_order"));

        assertThatThrownBy(() -> service(ratings, restaurants, eligibility, ratingLock)
                .submitRating(new SubmitRestaurantRatingCommand(7L, 42L, 99L, 5, null)))
                .isInstanceOf(RestaurantRatingConflictException.class)
                .hasMessage("Order has already been rated for this restaurant");

        verify(ratings, never()).aggregateByRestaurantAndStatus(any(), any());
        verify(restaurants, never()).save(any(Restaurant.class));
    }

    @Test
    void existingRatingIsReportedAsConflictBeforeAnotherInsert() {
        RestaurantRatingRepository ratings = mock(RestaurantRatingRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        RatingOrderEligibilityPort eligibility = mock(RatingOrderEligibilityPort.class);
        RestaurantRatingLock ratingLock = mock(RestaurantRatingLock.class);
        when(restaurants.findById(7L)).thenReturn(Optional.of(restaurant(7L)));
        when(ratings.existsByOrderId(99L)).thenReturn(true);

        assertThatThrownBy(() -> service(ratings, restaurants, eligibility, ratingLock)
                .submitRating(new SubmitRestaurantRatingCommand(7L, 42L, 99L, 5, null)))
                .isInstanceOf(RestaurantRatingConflictException.class)
                .hasMessage("Order has already been rated for this restaurant");

        verify(ratings, never()).saveAndFlush(any());
        verify(restaurants, never()).save(any(Restaurant.class));
    }

    @Test
    void publicRatingCompatibilityListIsBounded() {
        RestaurantRatingRepository ratings = mock(RestaurantRatingRepository.class);
        when(ratings.findByRestaurantIdAndStatus(eq(7L), eq(RatingStatus.APPROVED), any(Pageable.class)))
                .thenReturn(List.of());
        RestaurantRatingUseCase service = service(
                ratings, mock(RestaurantRepository.class), mock(RatingOrderEligibilityPort.class),
                mock(RestaurantRatingLock.class));

        assertThat(service.getRestaurantRatings(7L)).isEmpty();

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(ratings).findByRestaurantIdAndStatus(eq(7L), eq(RatingStatus.APPROVED), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void statusUpdateLocksRestaurantBeforeRecalculatingAggregate() {
        RestaurantRatingRepository ratings = mock(RestaurantRatingRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        RatingOrderEligibilityPort eligibility = mock(RatingOrderEligibilityPort.class);
        RestaurantRatingLock ratingLock = mock(RestaurantRatingLock.class);
        RestaurantRating rating = new RestaurantRating();
        rating.setId(21L);
        rating.setRestaurantId(7L);
        rating.setCustomerId(42L);
        rating.setOrderId(99L);
        rating.setRating(5);
        Restaurant restaurant = restaurant(7L);
        when(ratings.findById(21L)).thenReturn(Optional.of(rating));
        when(ratings.saveAndFlush(rating)).thenReturn(rating);
        when(restaurants.findById(7L)).thenReturn(Optional.of(restaurant));
        when(ratings.aggregateByRestaurantAndStatus(7L, RatingStatus.APPROVED))
                .thenReturn(new RestaurantRatingAggregate(1, 5.0));

        service(ratings, restaurants, eligibility, ratingLock).updateRatingStatus(21L, "approved");

        assertThat(restaurant.getRating()).isEqualTo(5.0);
        assertThat(restaurant.getRatingCount()).isEqualTo(1);
        InOrder order = inOrder(ratingLock, ratings, restaurants);
        order.verify(ratingLock).lock(7L);
        order.verify(ratings).saveAndFlush(rating);
        order.verify(ratings).aggregateByRestaurantAndStatus(7L, RatingStatus.APPROVED);
        order.verify(restaurants).save(restaurant);
        verify(restaurants, never()).findByIdForUpdate(7L);
    }

    private RestaurantRatingUseCase service(
            RestaurantRatingRepository ratings,
            RestaurantRepository restaurants,
            RatingOrderEligibilityPort eligibility,
            RestaurantRatingLock ratingLock) {
        return new DefaultRestaurantRatingUseCase(new JpaRestaurantRatingAdapter(ratings, restaurants, ratingLock),
                eligibility, new RestaurantTransactionPort() {
                    public <T> T repeatableRead(java.util.function.Supplier<T> operation) { return operation.get(); }
                    public <T> T readOnly(java.util.function.Supplier<T> operation) { return operation.get(); }
                    @Override public <T> T required(java.util.function.Supplier<T> operation) { return operation.get(); }
                });
    }

    private static Restaurant restaurant(Long id) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(id);
        return restaurant;
    }
}
