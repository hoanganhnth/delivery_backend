package com.delivery.shipper_service.mapper;

import com.delivery.shipper_service.dto.response.ShipperBalanceResponse;
import com.delivery.shipper_service.entity.ShipperBalance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShipperBalanceMapper {

    @Mapping(target = "totalBalance", expression = "java(shipperBalance.getTotalBalance())")
    ShipperBalanceResponse toResponse(ShipperBalance shipperBalance);
}
