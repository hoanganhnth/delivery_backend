package com.delivery.delivery_service.mapper;

import com.delivery.delivery_service.common.constants.PricingConstants;
import com.delivery.delivery_service.dto.request.AssignDeliveryRequest;
import com.delivery.delivery_service.dto.response.DeliveryResponse;
import com.delivery.delivery_service.dto.response.DeliveryTrackingResponse;
import com.delivery.delivery_service.entity.Delivery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface DeliveryMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "creatorId", ignore = true)
    @Mapping(target = "assignedAt", ignore = true)
    @Mapping(target = "pickedUpAt", ignore = true)
    @Mapping(target = "deliveredAt", ignore = true)
    @Mapping(target = "estimatedDeliveryTime", ignore = true)
    @Mapping(target = "shipperCurrentLat", ignore = true)
    @Mapping(target = "shipperCurrentLng", ignore = true)
    @Mapping(target = "rejectReason", ignore = true)
    @Mapping(target = "shippingFee", ignore = true)
    @Mapping(target = "status", expression = "java(com.delivery.delivery_service.entity.DeliveryStatus.ASSIGNED)")
    Delivery assignRequestToDelivery(AssignDeliveryRequest request);

    @Mapping(target = "estimatedEarnings", source = "shippingFee", qualifiedByName = "calculateShipperEarnings")
    @Mapping(target = "platformCommission", source = "shippingFee", qualifiedByName = "calculatePlatformCommission")
    DeliveryResponse deliveryToDeliveryResponse(Delivery delivery);

    List<DeliveryResponse> deliveriesToDeliveryResponses(List<Delivery> deliveries);

    @Mapping(source = "id", target = "deliveryId")
    @Mapping(target = "distanceToDestination", ignore = true)
    @Mapping(target = "estimatedMinutes", ignore = true)
    @Mapping(target = "statusMessage", ignore = true)
    DeliveryTrackingResponse deliveryToTrackingResponse(Delivery delivery);

    /**
     * ✅ Tính thu nhập shipper (85% của shipping fee)
     */
    @Named("calculateShipperEarnings")
    default BigDecimal calculateShipperEarnings(BigDecimal shippingFee) {
        if (shippingFee == null) {
            return BigDecimal.ZERO;
        }
        return PricingConstants.calculateShipperEarnings(shippingFee);
    }

    /**
     * ✅ Tính hoa hồng platform (15% của shipping fee)
     */
    @Named("calculatePlatformCommission")
    default BigDecimal calculatePlatformCommission(BigDecimal shippingFee) {
        if (shippingFee == null) {
            return BigDecimal.ZERO;
        }
        return PricingConstants.calculatePlatformCommission(shippingFee);
    }
}
