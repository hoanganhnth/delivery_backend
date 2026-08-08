package com.delivery.restaurant_service;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.auth.resourceserver.security.AuthenticatedActorAuthenticationToken;
import com.delivery.restaurant_service.common.constants.ApiPathConstants;
import com.delivery.restaurant_service.common.constants.HttpHeaderConstants;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional // Rollback DB sau mỗi test
class RestaurantControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private static RequestPostProcessor testActor(Long userId, String role) {
		AuthenticatedActor actor = new AuthenticatedActor(userId, "owner@example.com", Set.of(role));
		Jwt jwt = Jwt.withTokenValue("mock-token")
				.header("alg", "RS256")
				.header("kid", "key1")
				.claim("sub", userId.toString())
				.claim("roles", List.of(role))
				.claim("token_type", "access")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(3600))
				.build();
		AuthenticatedActorAuthenticationToken authenticationToken = new AuthenticatedActorAuthenticationToken(jwt, actor, Collections.emptyList());
		return authentication(authenticationToken);
	}

	@Test
	void createRestaurant_ShouldPersistToDatabase_WhenValidRequest() throws Exception {
		// Given
		CreateRestaurantRequest request = new CreateRestaurantRequest();
		request.setName("Integration Test Restaurant");
		request.setAddress("123 Test Street");
		request.setPhone("0123456789");
		request.setAddressLat(10.78);
		request.setAddressLng(106.69);

		// When & Then
		mockMvc.perform(post(ApiPathConstants.RESTAURANTS)
						.with(testActor(1L, RoleConstants.OWNER))
						.contentType(MediaType.APPLICATION_JSON)
						.header(HttpHeaderConstants.X_USER_ID, "1")
						.header(HttpHeaderConstants.X_ROLE, RoleConstants.OWNER)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(1))
				.andExpect(jsonPath("$.data.name").value("Integration Test Restaurant"))
				.andExpect(jsonPath("$.data.address").value("123 Test Street"));
	}

	@Test
	void getRestaurant_ShouldReturnRestaurant_WhenExists() throws Exception {
		// Given - create first
		CreateRestaurantRequest createRequest = new CreateRestaurantRequest();
		createRequest.setName("Test Restaurant");
		createRequest.setAddress("Test Address");
		createRequest.setPhone("0123456789");
		createRequest.setAddressLat(10.78);
		createRequest.setAddressLng(106.69);

		// Create
		String responseString = mockMvc.perform(post(ApiPathConstants.RESTAURANTS)
						.with(testActor(1L, RoleConstants.OWNER))
						.contentType(MediaType.APPLICATION_JSON)
						.header(HttpHeaderConstants.X_USER_ID, "1")
						.header(HttpHeaderConstants.X_ROLE, RoleConstants.OWNER)
						.content(objectMapper.writeValueAsString(createRequest)))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		// Extract ID from response
		JsonNode jsonNode = objectMapper.readTree(responseString);
		Long restaurantId = jsonNode.get("data").get("id").asLong();

		// When & Then - fetch by ID
		mockMvc.perform(get(ApiPathConstants.RESTAURANTS + "/" + restaurantId)
						.with(testActor(1L, RoleConstants.OWNER)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(1))
				.andExpect(jsonPath("$.data.name").value("Test Restaurant"));
	}
}
