package com.delivery.shipper_service.mapper;

import com.delivery.shipper_service.dto.response.ShipperRatingResponse;
import com.delivery.shipper_service.entity.ShipperRating;
import org.springframework.stereotype.Component;

@Component
public class ShipperRatingMapper {

    public ShipperRatingResponse toResponse(ShipperRating entity) {
        if (entity == null) {
            return null;
        }

        ShipperRatingResponse response = new ShipperRatingResponse();
        response.setId(entity.getId());
        response.setShipperId(entity.getShipperId());
        response.setCustomerId(entity.getCustomerId());
        response.setOrderId(entity.getOrderId());
        response.setRating(entity.getRating());
        response.setComment(entity.getComment());
        response.setCreatedAt(entity.getCreatedAt());

        return response;
    }
}
