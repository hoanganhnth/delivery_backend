package com.delivery.restaurant_service;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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

	@Test
	void createRestaurant_ShouldPersistToDatabase_WhenValidRequest() throws Exception {
		// Given
		CreateRestaurantRequest request = new CreateRestaurantRequest();
		request.setName("Integration Test Restaurant");
		request.setAddress("123 Test Street");
		request.setPhone("0123456789");

		// When & Then
		mockMvc.perform(post(ApiPathConstants.RESTAURANTS)
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

		// Create
		String responseString = mockMvc.perform(post(ApiPathConstants.RESTAURANTS)
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
		mockMvc.perform(get(ApiPathConstants.RESTAURANTS + "/" + restaurantId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(1))
				.andExpect(jsonPath("$.data.name").value("Test Restaurant"));
	}
}
