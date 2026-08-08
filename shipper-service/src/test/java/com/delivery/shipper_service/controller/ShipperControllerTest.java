package com.delivery.shipper_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.auth.resourceserver.security.AuthenticatedActorAuthenticationToken;
import com.delivery.shipper_service.dto.request.CreateShipperRequest;
import com.delivery.shipper_service.dto.response.ShipperResponse;
import com.delivery.shipper_service.service.ShipperService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

    private static RequestPostProcessor testActor(Long userId, String role) {
        AuthenticatedActor actor = new AuthenticatedActor(userId, "shipper@example.com", Set.of(role));
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .header("kid", "key1")
                .claim("sub", userId.toString())
                .claim("roles", List.of(role))
                .claim("token_type", "access")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role), new SimpleGrantedAuthority(role));
        AuthenticatedActorAuthenticationToken authenticationToken = new AuthenticatedActorAuthenticationToken(jwt, actor, authorities);
        return authentication(authenticationToken);
    }

    @Test
    public void testCreateShipper() throws Exception {
        CreateShipperRequest request = new CreateShipperRequest();
        request.setFullName("Test Shipper");
        request.setVehicleType("MOTORBIKE");
        request.setLicenseNumber("B1-123456");
        request.setIdCard("123456789");
        request.setPhone("0901234567");

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
                .with(testActor(1L, "SHIPPER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
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

        mockMvc.perform(get("/api/shippers/1")
                .with(testActor(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.isOnline").value(true));
    }
}
