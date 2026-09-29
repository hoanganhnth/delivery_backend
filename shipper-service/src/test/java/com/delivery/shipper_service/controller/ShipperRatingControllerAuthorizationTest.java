package com.delivery.shipper_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.auth.resourceserver.security.AuthenticatedActorAuthenticationToken;
import com.delivery.shipper.application.api.ShipperUseCases;
import com.delivery.shipper.application.api.ShipperCommands;
import com.delivery.shipper.application.api.ShipperResults;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShipperRatingController.class)
public class ShipperRatingControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShipperUseCases.ReadSelfRatings readSelfRatings;

    private static RequestPostProcessor testActor(Long userId, String role) {
        AuthenticatedActor actor = new AuthenticatedActor(userId, "test@example.com", Set.of(role));
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", userId.toString())
                .claim("roles", List.of(role))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role), new SimpleGrantedAuthority(role));
        return authentication(new AuthenticatedActorAuthenticationToken(jwt, actor, authorities));
    }

    @Test
    void getMyRatingsRequiresShipperRole() throws Exception {
        when(readSelfRatings.execute(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/shippers/me/ratings")
                .with(testActor(10L, "SHIPPER")))
                .andExpect(status().isOk());
    }

    @Test
    void getMyRatingsRejectsCustomerRole() throws Exception {
        mockMvc.perform(get("/api/shippers/me/ratings")
                .with(testActor(10L, "CUSTOMER")))
                .andExpect(status().isForbidden());
    }
}
