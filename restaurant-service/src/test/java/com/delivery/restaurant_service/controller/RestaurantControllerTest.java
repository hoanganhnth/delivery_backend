package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.HttpHeaderConstants;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
import com.delivery.restaurant_service.dto.response.RestaurantResponse;
import com.delivery.restaurant_service.service.RestaurantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RestaurantControllerTest {

    @Mock
    private RestaurantService restaurantService;

    @InjectMocks
    private RestaurantController restaurantController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(restaurantController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void create_ShouldReturnCreatedRestaurant_WhenValidRequest() throws Exception {
        // Given
        CreateRestaurantRequest request = new CreateRestaurantRequest();
        request.setName("Pizza Palace");
        request.setAddress("123 Main Street");
        request.setPhone("0123456789");
//        request.setDescription("Best pizza in town");

        RestaurantResponse response = new RestaurantResponse();
        response.setId(1L);
        response.setName("Pizza Palace");
        response.setAddress("123 Main Street");
        response.setPhone("0123456789");
//        response.setDescription("Best pizza in town");

        when(restaurantService.createRestaurant(any(CreateRestaurantRequest.class), anyLong(), anyString()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaderConstants.X_USER_ID, "1")
                        .header(HttpHeaderConstants.X_ROLE, RoleConstants.OWNER)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Pizza Palace"))
                .andExpect(jsonPath("$.data.address").value("123 Main Street"))
                .andExpect(jsonPath("$.data.phone").value("0123456789"));

        verify(restaurantService).createRestaurant(any(CreateRestaurantRequest.class), eq(1L), eq(RoleConstants.OWNER));
    }

    @Test
    void update_ShouldReturnUpdatedRestaurant_WhenValidRequest() throws Exception {
        // Given
        Long restaurantId = 1L;
        UpdateRestaurantRequest request = new UpdateRestaurantRequest();
        request.setName("Updated Pizza Palace");
        request.setAddress("456 New Street");

        RestaurantResponse response = new RestaurantResponse();
        response.setId(restaurantId);
        response.setName("Updated Pizza Palace");
        response.setAddress("456 New Street");

        when(restaurantService.updateRestaurant(eq(restaurantId), any(UpdateRestaurantRequest.class), anyLong()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(put("/api/restaurants/{id}", restaurantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaderConstants.X_USER_ID, "1")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Updated Pizza Palace"))
                .andExpect(jsonPath("$.data.address").value("456 New Street"));

        verify(restaurantService).updateRestaurant(eq(restaurantId), any(UpdateRestaurantRequest.class), eq(1L));
    }

    @Test
    void delete_ShouldReturnSuccess_WhenValidId() throws Exception {
        // Given
        Long restaurantId = 1L;
        Long userId = 1L;

        // When & Then
        mockMvc.perform(delete("/api/restaurants/{id}", restaurantId)
                        .header(HttpHeaderConstants.X_USER_ID, userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(restaurantService).deleteRestaurant(eq(restaurantId), eq(userId));
    }

    @Test
    void getById_ShouldReturnRestaurant_WhenValidId() throws Exception {
        // Given
        Long restaurantId = 1L;
        RestaurantResponse response = createRestaurantResponse(restaurantId, "Pizza Palace", "123 Main Street");

        when(restaurantService.getRestaurantById(restaurantId)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/restaurants/{id}", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Pizza Palace"))
                .andExpect(jsonPath("$.data.address").value("123 Main Street"));

        verify(restaurantService).getRestaurantById(restaurantId);
    }

    @Test
    void getAll_ShouldReturnAllRestaurants() throws Exception {
        // Given
        List<RestaurantResponse> restaurants = Arrays.asList(
                createRestaurantResponse(1L, "Pizza Palace", "123 Main Street"),
                createRestaurantResponse(2L, "Burger King", "456 Second Street"),
                createRestaurantResponse(3L, "Sushi Master", "789 Third Street")
        );

        when(restaurantService.getAllRestaurants()).thenReturn(restaurants);

        // When & Then
        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Pizza Palace"))
                .andExpect(jsonPath("$.data[1].id").value(2))
                .andExpect(jsonPath("$.data[1].name").value("Burger King"))
                .andExpect(jsonPath("$.data[2].id").value(3))
                .andExpect(jsonPath("$.data[2].name").value("Sushi Master"));

        verify(restaurantService).getAllRestaurants();
    }

    @Test
    void search_ShouldReturnMatchingRestaurants_WhenValidKeyword() throws Exception {
        // Given
        String keyword = "Pizza";
        List<RestaurantResponse> searchResults = Arrays.asList(
                createRestaurantResponse(1L, "Pizza Palace", "123 Main Street"),
                createRestaurantResponse(2L, "Best Pizza", "456 Pizza Street")
        );

        when(restaurantService.findByName(keyword)).thenReturn(searchResults);

        // When & Then
        mockMvc.perform(get("/api/restaurants/search").param("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Pizza Palace"))
                .andExpect(jsonPath("$.data[1].name").value("Best Pizza"));

        verify(restaurantService).findByName(keyword);
    }

    @Test
    void search_ShouldReturnEmptyList_WhenNoMatches() throws Exception {
        // Given
        String keyword = "NonExistent";
        List<RestaurantResponse> emptyResults = Arrays.asList();

        when(restaurantService.findByName(keyword)).thenReturn(emptyResults);

        // When & Then
        mockMvc.perform(get("/api/restaurants/search").param("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(restaurantService).findByName(keyword);
    }

    @Test
    void create_ShouldWork_WithoutHeaders() throws Exception {
        // Given
        CreateRestaurantRequest request = new CreateRestaurantRequest();
        request.setName("Test Restaurant");
        request.setAddress("Test Address");

        RestaurantResponse response = createRestaurantResponse(1L, "Test Restaurant", "Test Address");

        when(restaurantService.createRestaurant(any(CreateRestaurantRequest.class), isNull(), isNull()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data.name").value("Test Restaurant"));

        verify(restaurantService).createRestaurant(any(CreateRestaurantRequest.class), isNull(), isNull());
    }

    @Test
    void update_ShouldWork_WithoutOptionalHeaders() throws Exception {
        // Given
        Long restaurantId = 1L;
        UpdateRestaurantRequest request = new UpdateRestaurantRequest();
        request.setName("Updated Name");

        RestaurantResponse response = createRestaurantResponse(restaurantId, "Updated Name", "Address");

        when(restaurantService.updateRestaurant(eq(restaurantId), any(UpdateRestaurantRequest.class), isNull()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(put("/api/restaurants/{id}", restaurantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data.name").value("Updated Name"));

        verify(restaurantService).updateRestaurant(eq(restaurantId), any(UpdateRestaurantRequest.class), isNull());
    }

    private RestaurantResponse createRestaurantResponse(Long id, String name, String address) {
        RestaurantResponse response = new RestaurantResponse();
        response.setId(id);
        response.setName(name);
        response.setAddress(address);
        return response;
    }
}