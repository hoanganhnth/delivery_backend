package com.delivery.shipper_service.service.impl;

import com.delivery.shipper_service.dto.request.ShipperRatingRequest;
import com.delivery.shipper_service.dto.response.ShipperRatingResponse;
import com.delivery.shipper_service.entity.Shipper;
import com.delivery.shipper_service.entity.ShipperRating;
import com.delivery.shipper_service.exception.ResourceNotFoundException;
import com.delivery.shipper_service.mapper.ShipperRatingMapper;
import com.delivery.shipper_service.repository.ShipperRatingRepository;
import com.delivery.shipper_service.repository.ShipperRepository;
import com.delivery.shipper_service.service.IShipperRatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
public class ShipperRatingServiceImpl implements IShipperRatingService {

    private final ShipperRatingRepository shipperRatingRepository;
    private final ShipperRepository shipperRepository;
    private final ShipperRatingMapper shipperRatingMapper;

    @Override
    @Transactional
    public ShipperRatingResponse submitRating(Long shipperId, Long customerId, ShipperRatingRequest request) {
        requirePositiveId(shipperId, "shipperId");
        requirePositiveId(customerId, "customerId");
        if (request == null) {
            throw new IllegalArgumentException("Rating request is required");
        }
        if (request.getOrderId() == null || request.getOrderId() <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }
        Shipper shipper = shipperRepository.findById(shipperId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipper not found with id: " + shipperId));

        if (shipperRatingRepository.existsByOrderId(request.getOrderId())) {
            throw new RuntimeException("Đơn hàng này đã được đánh giá.");
        }

        ShipperRating rating = new ShipperRating();
        rating.setShipperId(shipperId);
        rating.setCustomerId(customerId);
        rating.setOrderId(request.getOrderId());
        rating.setRating(request.getRating());
        rating.setComment(request.getComment());

        rating = shipperRatingRepository.save(rating);

        // Cập nhật điểm trung bình của shipper
        double averageRating = java.util.Optional.ofNullable(
                        shipperRatingRepository.findAverageRatingByShipperId(shipperId))
                .orElseThrow(() -> new IllegalStateException(
                        "Rating aggregate is missing after persistence"));
        
        shipper.setRating(BigDecimal.valueOf(averageRating).setScale(1, RoundingMode.HALF_UP));
        shipperRepository.save(shipper);

        return shipperRatingMapper.toResponse(rating);
    }

    @Override
    public List<ShipperRatingResponse> getMyRatings(Long userId) {
        requirePositiveId(userId, "userId");
        Shipper shipper = shipperRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipper not found for user id: " + userId));

        return shipperRatingRepository.findByShipperIdOrderByCreatedAtDesc(
                        shipper.getId(), PageRequest.of(0, 100))
                .stream()
                .map(shipperRatingMapper::toResponse)
                .collect(Collectors.toList());
    }

    private void requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
