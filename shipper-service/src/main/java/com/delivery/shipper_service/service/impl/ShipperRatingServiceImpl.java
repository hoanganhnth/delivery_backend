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

@Service
@RequiredArgsConstructor
public class ShipperRatingServiceImpl implements IShipperRatingService {

    private final ShipperRatingRepository shipperRatingRepository;
    private final ShipperRepository shipperRepository;
    private final ShipperRatingMapper shipperRatingMapper;

    @Override
    @Transactional
    public ShipperRatingResponse submitRating(Long shipperId, Long customerId, ShipperRatingRequest request) {
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
        List<ShipperRating> allRatings = shipperRatingRepository.findByShipperIdOrderByCreatedAtDesc(shipperId);
        double averageRating = allRatings.stream()
                .mapToInt(ShipperRating::getRating)
                .average()
                .orElse(5.0);
        
        shipper.setRating(BigDecimal.valueOf(averageRating).setScale(1, RoundingMode.HALF_UP));
        shipperRepository.save(shipper);

        return shipperRatingMapper.toResponse(rating);
    }

    @Override
    public List<ShipperRatingResponse> getShipperRatings(Long shipperId) {
        if (!shipperRepository.existsById(shipperId)) {
            throw new ResourceNotFoundException("Shipper not found with id: " + shipperId);
        }

        return shipperRatingRepository.findByShipperIdOrderByCreatedAtDesc(shipperId)
                .stream()
                .map(shipperRatingMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ShipperRatingResponse> getMyRatings(Long userId) {
        Shipper shipper = shipperRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipper not found for user id: " + userId));

        return shipperRatingRepository.findByShipperIdOrderByCreatedAtDesc(shipper.getId())
                .stream()
                .map(shipperRatingMapper::toResponse)
                .collect(Collectors.toList());
    }
}
