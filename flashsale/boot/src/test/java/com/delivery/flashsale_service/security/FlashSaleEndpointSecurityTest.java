package com.delivery.flashsale_service.security;

import com.delivery.auth.resourceserver.security.DeliveryJwtAuthenticationConverter;
import com.delivery.flashsale_service.service.FlashSaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = com.delivery.flashsale_service.controller.PublicFlashSaleController.class)
@Import({SecurityConfig.class, DeliveryJwtAuthenticationConverter.class})
class FlashSaleEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlashSaleService flashSaleService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void publicCampaignsAllowAnonymousRead() throws Exception {
        when(flashSaleService.getActiveCampaigns()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/flashsales/public/campaigns"))
                .andExpect(status().isOk());
    }

    @Test
    void publicCampaignItemsAllowAnonymousRead() throws Exception {
        when(flashSaleService.getPublicItemsByCampaign(42L)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/flashsales/public/campaigns/42/items"))
                .andExpect(status().isOk());
    }

    @Test
    void nonPublicFlashSaleRoutesStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/flashsales/admin/campaigns"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousWriteUnderPublicPrefixIsNotAllowed() throws Exception {
        mockMvc.perform(post("/api/flashsales/public/campaigns"))
                .andExpect(status().isUnauthorized());
    }
}
