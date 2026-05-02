package com.delivery.shipper_service.service;

import com.delivery.shipper_service.dto.request.ShipperRatingRequest;
import com.delivery.shipper_service.dto.response.ShipperRatingResponse;

import java.util.List;

public interface IShipperRatingService {
    ShipperRatingResponse submitRating(Long shipperId, Long customerId, ShipperRatingRequest request);
    List<ShipperRatingResponse> getShipperRatings(Long shipperId);
    List<ShipperRatingResponse> getMyRatings(Long userId);
}
