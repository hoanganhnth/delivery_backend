package com.delivery.flashsale_service.controller;

import com.delivery.flashsale_service.exception.GlobalExceptionHandler;
import com.delivery.flashsale_service.exception.ResourceNotFoundException;
import com.delivery.flashsale_service.service.FlashSaleService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class FlashSaleExceptionHandlerTest {

    @Test
    void missingPublicCampaignReturnsNotFoundInsteadOfServerError() throws Exception {
        FlashSaleService service = mock(FlashSaleService.class);
        when(service.getPublicItemsByCampaign(99L))
                .thenThrow(new ResourceNotFoundException("Campaign not found"));
        MockMvc mvc = standaloneSetup(new PublicFlashSaleController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/api/flashsales/public/campaigns/99/items"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.message").value("Campaign not found"));
    }

    @Test
    void unexpectedFailureUsesSanitizedCanonicalEnvelope() throws Exception {
        FlashSaleService service = mock(FlashSaleService.class);
        when(service.getPublicItemsByCampaign(99L))
                .thenThrow(new IllegalStateException("database password leaked"));
        MockMvc mvc = standaloneSetup(new PublicFlashSaleController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/api/flashsales/public/campaigns/99/items"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }
}
