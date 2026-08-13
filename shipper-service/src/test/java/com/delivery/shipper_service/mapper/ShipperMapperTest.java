package com.delivery.shipper_service.mapper;

import com.delivery.shipper_service.dto.request.CreateShipperRequest;
import com.delivery.shipper_service.dto.request.UpdateShipperRequest;
import com.delivery.shipper_service.entity.Shipper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShipperMapperTest {

    private final ShipperMapper mapper = new ShipperMapper();

    @Test
    void mapsCreateAndResponseWithoutGeneratedCode() {
        CreateShipperRequest request = new CreateShipperRequest();
        request.setFullName("Binh");
        request.setVehicleType("MOTORBIKE");
        request.setLicenseNumber("A1");
        request.setPhone("0900000000");

        Shipper shipper = mapper.toEntity(request);
        shipper.setId(1L);
        shipper.setUserId(2L);

        assertThat(shipper.getFullName()).isEqualTo("Binh");
        assertThat(mapper.toResponse(shipper).getUserId()).isEqualTo(2L);
        assertThat(mapper.toResponse(shipper).getVehicleType()).isEqualTo("MOTORBIKE");
    }

    @Test
    void updateIgnoresNullAndProtectedFields() {
        Shipper shipper = new Shipper();
        shipper.setUserId(2L);
        shipper.setFullName("Binh");
        shipper.setPhone("old");
        shipper.setIsOnline(true);
        UpdateShipperRequest request = new UpdateShipperRequest();
        request.setPhone("new");
        request.setIsOnline(false);

        mapper.updateEntityFromRequest(request, shipper);

        assertThat(shipper.getUserId()).isEqualTo(2L);
        assertThat(shipper.getFullName()).isEqualTo("Binh");
        assertThat(shipper.getPhone()).isEqualTo("new");
        assertThat(shipper.getIsOnline()).isTrue();
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toResponse(null)).isNull();
    }
}
