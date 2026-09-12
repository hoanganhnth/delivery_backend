package com.delivery.livestream_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.livestream_service.dto.response.LivestreamModerationResponse;
import com.delivery.livestream_service.enums.LivestreamModerationAction;
import com.delivery.livestream_service.exception.GlobalExceptionHandler;
import com.delivery.livestream_service.service.LivestreamModerationService;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LivestreamModerationControllerTest {
    private final LivestreamModerationService moderation = mock(LivestreamModerationService.class);
    private final UUID id = UUID.randomUUID();
    private AuthenticatedActor actor;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        actor = new AuthenticatedActor(90L, 9L, "admin@example.test", Set.of("ADMIN"));
        mvc = MockMvcBuilders.standaloneSetup(new LivestreamModerationController(moderation))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == AuthenticatedActor.class;
                    }
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory factory) { return actor; }
                }).build();
    }

    @Test
    void returnsNormalEnvelopeWithAuditIdentityAndServerTime() throws Exception {
        when(moderation.moderate(eq(id), any(), eq(actor))).thenReturn(new LivestreamModerationResponse(
                55L, id, LivestreamModerationAction.WARN, null, Instant.parse("2026-09-12T01:00:00Z")));
        mvc.perform(post("/api/livestreams/{id}/moderation", id).contentType("application/json")
                .content("{\"action\":\"WARN\",\"reason\":\"Policy violation\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data.auditId").value(55))
                .andExpect(jsonPath("$.data.appliedAt").value("2026-09-12T01:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "SHOP_OWNER", "SHIPPER"})
    void nonAdminCannotModerate(String role) throws Exception {
        actor = new AuthenticatedActor(1L, 1L, "caller@example.test", Set.of(role));
        mvc.perform(post("/api/livestreams/{id}/moderation", id).contentType("application/json")
                .content("{\"action\":\"WARN\",\"reason\":\"Policy violation\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(moderation);
    }

    @Test
    void missingActorCannotModerate() throws Exception {
        actor = null;
        mvc.perform(post("/api/livestreams/{id}/moderation", id).contentType("application/json")
                .content("{\"action\":\"WARN\",\"reason\":\"Policy violation\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(moderation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"action\":\"WARN\"}", "{\"action\":\"WARN\",\"reason\":\"  \"}",
            "{\"action\":\"UNPIN\",\"reason\":\"Policy\"}",
            "{\"action\":\"UNPIN\",\"reason\":\"Policy\",\"productId\":0}",
            "{\"action\":\"WARN\",\"reason\":\"Policy\",\"productId\":1}",
            "{\"action\":\"HIDE\",\"reason\":\"Policy\"}"})
    void invalidRequestRejectedBeforeMutation(String body) throws Exception {
        mvc.perform(post("/api/livestreams/{id}/moderation", id).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(0));
        verifyNoInteractions(moderation);
    }

    @Test
    void oversizedReasonRejected() throws Exception {
        invalidRequestRejectedBeforeMutation("{\"action\":\"WARN\",\"reason\":\"" + "x".repeat(1001) + "\"}");
    }
}
