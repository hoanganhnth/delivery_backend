package com.delivery.shipper_service.mapper;

import com.delivery.shipper_service.dto.response.ShipperTransactionResponse;
import com.delivery.shipper_service.entity.ShipperTransaction;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ShipperTransactionMapper {

    ShipperTransactionResponse toResponse(ShipperTransaction shipperTransaction);
}
