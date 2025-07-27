package com.delivery.shipper_service.mapper;

import com.delivery.shipper_service.dto.request.CreateShipperRequest;
import com.delivery.shipper_service.dto.request.UpdateShipperRequest;
import com.delivery.shipper_service.dto.response.ShipperResponse;
import com.delivery.shipper_service.entity.Shipper;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ShipperMapper {

    ShipperResponse toResponse(Shipper shipper);

    Shipper toEntity(CreateShipperRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(UpdateShipperRequest request, @MappingTarget Shipper shipper);
}
