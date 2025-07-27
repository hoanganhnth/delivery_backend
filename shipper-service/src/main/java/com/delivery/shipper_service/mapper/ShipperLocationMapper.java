package com.delivery.shipper_service.mapper;

import com.delivery.shipper_service.dto.response.ShipperLocationResponse;
import com.delivery.shipper_service.entity.ShipperLocation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ShipperLocationMapper {

    ShipperLocationResponse toResponse(ShipperLocation shipperLocation);
}
