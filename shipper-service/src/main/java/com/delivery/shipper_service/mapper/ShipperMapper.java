package com.delivery.shipper_service.mapper;

import com.delivery.shipper_service.dto.response.ShipperResponse;
import com.delivery.shipper.application.api.ShipperSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ShipperMapper {

    public ShipperResponse toResponse(ShipperSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        ShipperResponse response = new ShipperResponse();
        response.setId(snapshot.id());
        if (snapshot.identity() != null) {
            response.setUserId(snapshot.identity().legacyUserId());
            response.setPrincipalId(snapshot.identity().principalId());
        }
        response.setFullName(snapshot.fullName());
        response.setVehicleType(snapshot.vehicleType());
        response.setLicenseNumber(snapshot.licenseNumber());
        response.setIdCard(snapshot.idCard());
        response.setPhone(snapshot.phone());
        response.setLicensePlate(snapshot.licensePlate());
        response.setIsOnline(snapshot.online());
        response.setCompletedDeliveries(snapshot.completedDeliveries());
        response.setRating(BigDecimal.valueOf(snapshot.rating()).setScale(1, RoundingMode.HALF_UP));
        return response;
    }
}
