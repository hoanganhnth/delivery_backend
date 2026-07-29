package com.delivery.shipper_service.mapper;

import com.delivery.shipper_service.dto.request.CreateShipperRequest;
import com.delivery.shipper_service.dto.request.UpdateShipperRequest;
import com.delivery.shipper_service.dto.response.ShipperResponse;
import com.delivery.shipper_service.entity.Shipper;
import org.springframework.stereotype.Component;

@Component
public class ShipperMapper {

    public ShipperResponse toResponse(Shipper shipper) {
        if (shipper == null) {
            return null;
        }
        ShipperResponse response = new ShipperResponse();
        response.setId(shipper.getId());
        response.setUserId(shipper.getUserId());
        response.setFullName(shipper.getFullName());
        response.setVehicleType(shipper.getVehicleType());
        response.setLicenseNumber(shipper.getLicenseNumber());
        response.setIdCard(shipper.getIdCard());
        response.setDriverImage(shipper.getDriverImage());
        response.setIsOnline(shipper.getIsOnline());
        response.setRating(shipper.getRating());
        response.setCompletedDeliveries(shipper.getCompletedDeliveries());
        response.setCreatedAt(shipper.getCreatedAt());
        response.setUpdatedAt(shipper.getUpdatedAt());
        response.setPhone(shipper.getPhone());
        response.setIdCardFrontImage(shipper.getIdCardFrontImage());
        response.setIdCardBackImage(shipper.getIdCardBackImage());
        response.setLicenseImage(shipper.getLicenseImage());
        response.setLicensePlate(shipper.getLicensePlate());
        return response;
    }

    public Shipper toEntity(CreateShipperRequest request) {
        if (request == null) {
            return null;
        }
        Shipper shipper = new Shipper();
        shipper.setFullName(request.getFullName());
        shipper.setVehicleType(request.getVehicleType());
        shipper.setLicenseNumber(request.getLicenseNumber());
        shipper.setIdCard(request.getIdCard());
        shipper.setPhone(request.getPhone());
        shipper.setDriverImage(request.getDriverImage());
        shipper.setIdCardFrontImage(request.getIdCardFrontImage());
        shipper.setIdCardBackImage(request.getIdCardBackImage());
        shipper.setLicenseImage(request.getLicenseImage());
        shipper.setLicensePlate(request.getLicensePlate());
        return shipper;
    }

    public void updateEntityFromRequest(UpdateShipperRequest request, Shipper shipper) {
        if (request == null) {
            return;
        }
        if (request.getVehicleType() != null) shipper.setVehicleType(request.getVehicleType());
        if (request.getLicenseNumber() != null) shipper.setLicenseNumber(request.getLicenseNumber());
        if (request.getIdCard() != null) shipper.setIdCard(request.getIdCard());
        if (request.getPhone() != null) shipper.setPhone(request.getPhone());
        if (request.getDriverImage() != null) shipper.setDriverImage(request.getDriverImage());
        if (request.getIsOnline() != null) shipper.setIsOnline(request.getIsOnline());
        if (request.getIdCardFrontImage() != null) shipper.setIdCardFrontImage(request.getIdCardFrontImage());
        if (request.getIdCardBackImage() != null) shipper.setIdCardBackImage(request.getIdCardBackImage());
        if (request.getLicenseImage() != null) shipper.setLicenseImage(request.getLicenseImage());
        if (request.getLicensePlate() != null) shipper.setLicensePlate(request.getLicensePlate());
    }
}
