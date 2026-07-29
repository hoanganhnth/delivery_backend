package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.dto.request.RestaurantRatingRequest;
import com.delivery.restaurant_service.entity.RatingStatus;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.exception.RestaurantRatingConflictException;
import com.delivery.restaurant_service.repository.RestaurantRatingRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.service.impl.RestaurantRatingServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestaurantRatingServiceImplTest {

    @Test
    void ratingSummaryUsesDatabaseAggregateInsteadOfLoadingEveryRating() {
        RestaurantRatingRepository ratings = mock(RestaurantRatingRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(7L);
        when(restaurants.findById(7L)).thenReturn(Optional.of(restaurant));
        when(ratings.existsByOrderId(99L)).thenReturn(false);
        when(ratings.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ratings.countByRestaurantIdAndStatus(7L, RatingStatus.APPROVED)).thenReturn(101L);
        when(ratings.averageRatingByRestaurantAndStatus(7L, RatingStatus.APPROVED)).thenReturn(4.5);
        RestaurantRatingRequest request = new RestaurantRatingRequest();
        request.setOrderId(99L);
        request.setRating(5);

        new RestaurantRatingServiceImpl(ratings, restaurants).submitRating(7L, 42L, request);

        assertThat(restaurant.getRating()).isEqualTo(4.5);
        assertThat(restaurant.getRatingCount()).isEqualTo(101);
        verify(restaurants).save(restaurant);
    }

    @Test
    void duplicateRatingConstraintIsReportedAsConflict() {
        RestaurantRatingRepository ratings = mock(RestaurantRatingRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(7L);
        when(restaurants.findById(7L)).thenReturn(Optional.of(restaurant));
        when(ratings.existsByOrderId(99L)).thenReturn(false);
        when(ratings.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_restaurant_ratings_order"));
        RestaurantRatingRequest request = new RestaurantRatingRequest();
        request.setOrderId(99L);
        request.setRating(5);

        assertThatThrownBy(() -> new RestaurantRatingServiceImpl(ratings, restaurants)
                .submitRating(7L, 42L, request))
                .isInstanceOf(RestaurantRatingConflictException.class)
                .hasMessage("Order has already been rated for this restaurant");

        verify(restaurants, never()).save(restaurant);
    }

    @Test
    void existingRatingIsReportedAsConflictBeforeAnotherInsert() {
        RestaurantRatingRepository ratings = mock(RestaurantRatingRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(7L);
        when(restaurants.findById(7L)).thenReturn(Optional.of(restaurant));
        when(ratings.existsByOrderId(99L)).thenReturn(true);
        RestaurantRatingRequest request = new RestaurantRatingRequest();
        request.setOrderId(99L);
        request.setRating(5);

        assertThatThrownBy(() -> new RestaurantRatingServiceImpl(ratings, restaurants)
                .submitRating(7L, 42L, request))
                .isInstanceOf(RestaurantRatingConflictException.class)
                .hasMessage("Order has already been rated for this restaurant");

        verify(ratings, never()).saveAndFlush(any());
        verify(restaurants, never()).save(restaurant);
    }

    @Test
    void publicRatingCompatibilityListIsBounded() {
        RestaurantRatingRepository ratings = mock(RestaurantRatingRepository.class);
        when(ratings.findByRestaurantIdAndStatus(eq(7L), eq(RatingStatus.APPROVED), any(Pageable.class)))
                .thenReturn(List.of());
        RestaurantRatingServiceImpl service = new RestaurantRatingServiceImpl(
                ratings, mock(RestaurantRepository.class));

        assertThat(service.getRestaurantRatings(7L)).isEmpty();

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(ratings).findByRestaurantIdAndStatus(eq(7L), eq(RatingStatus.APPROVED), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(100);
    }
}
