package com.delivery.shipper_service.service.impl;

import com.delivery.shipper_service.dto.request.ShipperRatingRequest;
import com.delivery.shipper_service.entity.Shipper;
import com.delivery.shipper_service.entity.ShipperRating;
import com.delivery.shipper_service.mapper.ShipperRatingMapper;
import com.delivery.shipper_service.repository.ShipperRatingRepository;
import com.delivery.shipper_service.repository.ShipperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ShipperRatingServiceImplTest {

    @Mock ShipperRatingRepository ratingRepository;
    @Mock ShipperRepository shipperRepository;
    @Mock ShipperRatingMapper mapper;
    private ShipperRatingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShipperRatingServiceImpl(ratingRepository, shipperRepository, mapper);
    }

    @Test
    void submitUsesDatabaseAggregateInsteadOfLoadingRatingHistory() {
        Shipper shipper = new Shipper();
        when(shipperRepository.findById(3L)).thenReturn(Optional.of(shipper));
        when(ratingRepository.existsByOrderId(9L)).thenReturn(false);
        when(ratingRepository.save(any(ShipperRating.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ratingRepository.findAverageRatingByShipperId(3L)).thenReturn(4.25);
        ShipperRatingRequest request = new ShipperRatingRequest();
        request.setOrderId(9L);
        request.setRating(5);

        service.submitRating(3L, 7L, request);

        verify(ratingRepository).findAverageRatingByShipperId(3L);
        assertThat(shipper.getRating()).isEqualByComparingTo("4.3");
    }

    @Test
    void submitFailsClosedWhenPersistedRatingHasNoAggregate() {
        Shipper shipper = new Shipper();
        when(shipperRepository.findById(3L)).thenReturn(Optional.of(shipper));
        when(ratingRepository.existsByOrderId(9L)).thenReturn(false);
        when(ratingRepository.save(any(ShipperRating.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ratingRepository.findAverageRatingByShipperId(3L)).thenReturn(null);
        ShipperRatingRequest request = new ShipperRatingRequest();
        request.setOrderId(9L);
        request.setRating(5);

        assertThatThrownBy(() -> service.submitRating(3L, 7L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("aggregate");
    }

    @Test
    void selfListResolvesUserIdentityAndCapsRepositoryQueryAtOneHundred() {
        Shipper shipper = new Shipper();
        shipper.setId(3L);
        when(shipperRepository.findByUserId(7L)).thenReturn(Optional.of(shipper));
        when(ratingRepository.findByShipperIdOrderByCreatedAtDesc(eq(3L), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service.getMyRatings(7L)).isEmpty();

        org.mockito.ArgumentCaptor<Pageable> pages = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(ratingRepository).findByShipperIdOrderByCreatedAtDesc(eq(3L), pages.capture());
        assertThat(pages.getAllValues()).allSatisfy(page -> assertThat(page.getPageSize()).isEqualTo(100));
    }

    @Test
    void submitRejectsNullRequestBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.submitRating(3L, 7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rating request is required");

        verifyNoInteractions(ratingRepository, shipperRepository, mapper);
    }

    @Test
    void submitRejectsInvalidIdentityBeforeRepositoryAccess() {
        ShipperRatingRequest request = new ShipperRatingRequest();
        request.setOrderId(9L);
        request.setRating(5);

        assertThatThrownBy(() -> service.submitRating(0L, 7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("shipperId must be positive");

        verifyNoInteractions(ratingRepository, shipperRepository, mapper);
    }
}
