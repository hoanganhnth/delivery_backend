package com.delivery.shipper_service.controller;

import com.delivery.shipper_service.dto.request.CreateShipperRequest;
import com.delivery.shipper_service.dto.response.ShipperResponse;
import com.delivery.shipper_service.service.ShipperService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShipperController.class)
public class ShipperControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShipperService shipperService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testCreateShipper() throws Exception {
        CreateShipperRequest request = new CreateShipperRequest();
        // request.setUserId(1L);
        request.setVehicleType("MOTORBIKE");
        request.setLicenseNumber("B1-123456");
        request.setIdCard("123456789");

        ShipperResponse response = new ShipperResponse();
        response.setId(1L);
        response.setUserId(1L);
        response.setVehicleType("MOTORBIKE");
        response.setLicenseNumber("B1-123456");
        response.setIdCard("123456789");
        response.setIsOnline(false);
        response.setRating(BigDecimal.valueOf(5.0));
        response.setCompletedDeliveries(0);

        when(shipperService.createShipper(any(CreateShipperRequest.class), any(), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/shippers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-User-Id", "1")
                .header("X-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.vehicleType").value("MOTORBIKE"));
    }

    @Test
    public void testGetShipperById() throws Exception {
        ShipperResponse response = new ShipperResponse();
        response.setId(1L);
        response.setUserId(1L);
        response.setVehicleType("MOTORBIKE");
        response.setIsOnline(true);

        when(shipperService.getShipperById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/shippers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.isOnline").value(true));
    }
}
