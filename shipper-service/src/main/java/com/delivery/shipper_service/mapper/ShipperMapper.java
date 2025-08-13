package com.delivery.shipper_service.mapper;

import com.delivery.shipper_service.dto.request.CreateShipperRequest;
import com.delivery.shipper_service.dto.request.UpdateShipperRequest;
import com.delivery.shipper_service.dto.response.ShipperResponse;
import com.delivery.shipper_service.entity.Shipper;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ShipperMapper {

    ShipperResponse toResponse(Shipper shipper);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "completedDeliveries", ignore = true)
    @Mapping(target = "rating", ignore = true)
    @Mapping(target = "isOnline", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Shipper toEntity(CreateShipperRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "completedDeliveries", ignore = true)
    @Mapping(target = "rating", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "userId", ignore = true)
    void updateEntityFromRequest(UpdateShipperRequest request, @MappingTarget Shipper shipper);
}
