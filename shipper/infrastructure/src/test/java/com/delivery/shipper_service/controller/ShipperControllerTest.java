package com.delivery.shipper_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.auth.resourceserver.security.AuthenticatedActorAuthenticationToken;
import com.delivery.shipper_service.dto.request.CreateShipperRequest;
import com.delivery.shipper_service.dto.response.ShipperResponse;
import com.delivery.shipper_service.mapper.ShipperMapper;
import com.delivery.shipper.application.api.ShipperUseCases;
import com.delivery.shipper.application.api.ShipperCommands;
import com.delivery.shipper.application.api.ShipperResults;
import com.delivery.shipper.application.api.ShipperSnapshot;
import com.delivery.shipper.domain.identity.IdentityRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShipperController.class)
@Import(ShipperMapper.class)
public class ShipperControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private ShipperUseCases.CreateProfile createProfile;
    @MockitoBean private ShipperUseCases.UpdateProfile updateProfile;
    @MockitoBean private ShipperUseCases.ReadSelf readSelf;
    @MockitoBean private ShipperUseCases.ReadById readById;
    @MockitoBean private ShipperUseCases.ReadPage readPage;
    @MockitoBean private ShipperUseCases.SetOnlineStatus setOnlineStatus;

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

        var identity = new IdentityRef(1L, 1L);
        var snapshot = new ShipperSnapshot(1L, identity, "Test Shipper", "MOTORBIKE", "B1-123456", "123456789", "0901234567", null, false, 0, 5.0, 0, "ACTIVE", 1L, null, null, null, null, null, null);
        when(createProfile.execute(any())).thenReturn(new ShipperResults.CreateProfileResult(snapshot));

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
        var identity = new IdentityRef(1L, 1L);
        var snapshot = new ShipperSnapshot(1L, identity, "Test Shipper", "MOTORBIKE", "B1-123456", "123456789", "0901234567", null, true, 0, 5.0, 0, "ACTIVE", 1L, null, null, null, null, null, null);
        
        when(readById.execute(any(), anyLong())).thenReturn(snapshot);

        mockMvc.perform(get("/api/shippers/1")
                .with(testActor(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.isOnline").value(true));
    }

    @Test
    void genericProfileUpdateRejectsOnlineStatusField() throws Exception {
        mockMvc.perform(put("/api/shippers")
                .with(testActor(1L, "SHIPPER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isOnline\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(updateProfile);
    }

    @Test
    void profileUpdateForwardsDocumentsAndUsesResolvedProfileIdentity() throws Exception {
        var request = new com.delivery.shipper_service.dto.request.UpdateShipperRequest();
        request.setFullName("Updated Shipper");
        request.setVehicleType("BIKE");
        request.setLicenseNumber("LIC-22");
        request.setIdCard("CARD-22");
        request.setPhone("0901111111");
        request.setLicensePlate("PLATE-22");
        request.setDriverImage("driver.png");
        request.setIdCardFrontImage("front.png");
        request.setIdCardBackImage("back.png");
        request.setLicenseImage("license.png");
        var snapshot = new ShipperSnapshot(42L, new IdentityRef(1L, 1L), "Updated Shipper", "BIKE",
                "LIC-22", "CARD-22", "0901111111", "PLATE-22", false, 0, 5.0, 0, "ACTIVE", 1L,
                "driver.png", "front.png", "back.png", "license.png", null, null);
        when(readSelf.execute(any())).thenReturn(snapshot);
        when(updateProfile.execute(any())).thenReturn(snapshot);

        mockMvc.perform(put("/api/shippers").with(testActor(1L, "SHIPPER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.fullName").value("Updated Shipper"))
                .andExpect(jsonPath("$.data.driverImage").value("driver.png"))
                .andExpect(jsonPath("$.data.idCardFrontImage").value("front.png"))
                .andExpect(jsonPath("$.data.idCardBackImage").value("back.png"))
                .andExpect(jsonPath("$.data.licenseImage").value("license.png"));
        org.mockito.Mockito.verify(updateProfile).execute(new ShipperCommands.UpdateProfile(
                new ShipperCommands.Actor(1L, 1L, com.delivery.shipper.domain.identity.ShipperRole.SHIPPER),
                42L, "Updated Shipper", "BIKE", "LIC-22", "CARD-22", "0901111111", "PLATE-22",
                "driver.png", "front.png", "back.png", "license.png"));
    }

    @Test
    void onlineStatusHasItsOwnCommandAndResponse() throws Exception {
        var snapshot = new ShipperSnapshot(42L, new IdentityRef(1L, 1L), "Shipper", "BIKE",
                "LIC", "CARD", "0901111111", null, true, 0, 5.0, 0, "ACTIVE", 1L,
                null, null, null, null, null, null);
        when(setOnlineStatus.execute(any())).thenReturn(snapshot);
        mockMvc.perform(patch("/api/shippers/online-status").param("isOnline", "true")
                .with(testActor(1L, "SHIPPER")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isOnline").value(true));
        org.mockito.Mockito.verify(setOnlineStatus).execute(new ShipperCommands.SetOnlineStatus(
                new ShipperCommands.Actor(1L, 1L, com.delivery.shipper.domain.identity.ShipperRole.SHIPPER), true));
        verifyNoInteractions(updateProfile);
    }
}
