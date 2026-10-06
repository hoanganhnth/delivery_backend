package com.delivery.search_service.controller;

import com.delivery.search_service.exception.SearchUnavailableException;
import com.delivery.search_service.security.SecurityConfig;
import com.delivery.search_service.service.SearchService;
import com.delivery.auth.resourceserver.security.DeliveryJwtAuthenticationConverter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.PageImpl;

import java.util.List;

@WebMvcTest(
        controllers = SearchController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({ SecurityConfig.class, DeliveryJwtAuthenticationConverter.class })
class SearchControllerValidationTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean SearchService searchService;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void rejectsBlankQuery() throws Exception {
        mockMvc.perform(get("/api/search/restaurants").param("q", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void capsPageSize() throws Exception {
        mockMvc.perform(get("/api/search/dishes")
                        .param("q", "pho")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNegativePage() throws Exception {
        mockMvc.perform(get("/api/search/dishes")
                        .param("q", "pho")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchInfrastructureFailureReturnsSanitizedServiceUnavailable() throws Exception {
        when(searchService.searchRestaurants(eq("pho"), any()))
                .thenThrow(new SearchUnavailableException("cluster endpoint leaked"));

        mockMvc.perform(get("/api/search/restaurants").param("q", "pho"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.message").value("Search is temporarily unavailable"));
    }

    @Test
    void successfulSearchUsesCanonicalEnvelope() throws Exception {
        when(searchService.searchRestaurants(eq("pho"), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/search/restaurants").param("q", "pho"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalItems").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.message").value("Thành công"));
    }

    @Test
    void onlyTheDocumentedGetSearchSurfaceIsPublic() throws Exception {
        mockMvc.perform(post("/api/search/restaurants"))
                .andExpect(status().isUnauthorized());
    }
}
