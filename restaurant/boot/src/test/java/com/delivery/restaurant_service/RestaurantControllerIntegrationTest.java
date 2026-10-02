package com.delivery.restaurant_service;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.auth.resourceserver.security.AuthenticatedActorAuthenticationToken;
import com.delivery.restaurant_service.common.constants.ApiPathConstants;
import com.delivery.restaurant_service.common.constants.HttpHeaderConstants;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.identity.client.IdentityPrincipalClient;
import com.delivery.identity.contracts.IdentityLifecycleStatus;
import com.delivery.identity.contracts.IdentityPrincipal;
import com.delivery.identity.contracts.IdentityRole;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

	@Autowired
	private RestaurantRepository restaurantRepository;

	@MockitoBean
	private IdentityPrincipalClient identityPrincipalClient;

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
		verifyNoInteractions(identityPrincipalClient);
	}

	@Test
	void adminCreateVerifiesAndPersistsRequestedOwnerPrincipal() throws Exception {
		CreateRestaurantRequest request = new CreateRestaurantRequest();
		request.setOwnerPrincipalId(42L);
		request.setName("Admin Assigned Restaurant");
		request.setAddress("123 Admin Test Street");
		request.setAddressLat(10.78);
		request.setAddressLng(106.69);
		when(identityPrincipalClient.findByPrincipalId(42L)).thenReturn(Optional.of(
				new IdentityPrincipal(42L, IdentityRole.SHOP_OWNER, IdentityLifecycleStatus.ACTIVE)));

		String response = mockMvc.perform(post(ApiPathConstants.RESTAURANTS)
						.with(testActor(1L, RoleConstants.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		long restaurantId = objectMapper.readTree(response).get("data").get("id").asLong();
		assertThat(restaurantRepository.findById(restaurantId).orElseThrow().getOwnerPrincipalId())
				.isEqualTo(42L);
	}

	@Test
	void createRejectsIncompleteOperatingHoursWithoutPersistingRestaurant() throws Exception {
		long before = restaurantRepository.count();
		CreateRestaurantRequest request = new CreateRestaurantRequest();
		request.setName("Incomplete Schedule Restaurant");
		request.setAddress("123 Valid Street");
		request.setAddressLat(10.78);
		request.setAddressLng(106.69);
		request.setOpeningHour(LocalTime.of(18, 0));

		mockMvc.perform(post(ApiPathConstants.RESTAURANTS)
					.with(testActor(1L, RoleConstants.OWNER))
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());

		assertThat(restaurantRepository.count()).isEqualTo(before);
		verifyNoInteractions(identityPrincipalClient);
	}

	@Test
	void updateRejectsResultingIncompleteScheduleAndPreservesStoredRestaurant() throws Exception {
		Restaurant restaurant = new Restaurant();
		restaurant.setName("Always Open Restaurant");
		restaurant.setAddress("123 Valid Street");
		restaurant.setCreatorId(1L);
		restaurant.setOwnerPrincipalId(1L);
		restaurant = restaurantRepository.saveAndFlush(restaurant);
		UpdateRestaurantRequest request = new UpdateRestaurantRequest();
		request.setOpeningHour(LocalTime.of(18, 0));

		mockMvc.perform(put(ApiPathConstants.RESTAURANTS + "/{id}", restaurant.getId())
					.with(testActor(1L, RoleConstants.OWNER))
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());

		Restaurant persisted = restaurantRepository.findById(restaurant.getId()).orElseThrow();
		assertThat(persisted.getOpeningHour()).isNull();
		assertThat(persisted.getClosingHour()).isNull();
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
