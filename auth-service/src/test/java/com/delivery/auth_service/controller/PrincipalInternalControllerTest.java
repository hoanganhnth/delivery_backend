package com.delivery.auth_service.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.auth.resourceserver.security.DeliveryJwtAuthenticationConverter;
import com.delivery.auth_service.security.SecurityConfig;
import com.delivery.auth_service.application.port.in.PrincipalLookupUseCase;
import com.delivery.identity.contracts.IdentityLifecycleStatus;
import com.delivery.identity.contracts.IdentityPrincipal;
import com.delivery.identity.contracts.IdentityRole;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

@WebMvcTest(
        controllers = PrincipalInternalController.class,
        properties = "app.internal.secret=service-secret",
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfig.class, DeliveryJwtAuthenticationConverter.class})
class PrincipalInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PrincipalLookupUseCase principalLookupUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RestTemplate restTemplate;

    @Test
    void validInternalTokenReturnsTypedPrincipalWithoutJwt() throws Exception {
        when(principalLookupUseCase.findByPrincipalId(42L)).thenReturn(Optional.of(
                new IdentityPrincipal(42L, IdentityRole.SHOP_OWNER, IdentityLifecycleStatus.ACTIVE)));

        mockMvc.perform(get("/api/auth/internal/principals/42")
                        .header("Internal-Token", "service-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principalId").value(42))
                .andExpect(jsonPath("$.role").value("SHOP_OWNER"))
                .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"));
    }

    @Test
    void missingPrincipalReturnsNotFoundAfterAuthorization() throws Exception {
        when(principalLookupUseCase.findByPrincipalId(404L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/internal/principals/404")
                        .header("Internal-Token", "service-secret"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingOrWrongInternalTokenFailsClosedBeforeLookup() throws Exception {
        mockMvc.perform(get("/api/auth/internal/principals/42"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/internal/principals/42")
                        .header("Internal-Token", "wrong-secret"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(principalLookupUseCase);
    }

    @Test
    void malformedOrNonPositivePrincipalIdReturnsBadRequest() throws Exception {
        for (String principalId : new String[] {"not-a-number", "0", "-1"}) {
            mockMvc.perform(get("/api/auth/internal/principals/{principalId}", principalId)
                            .header("Internal-Token", "service-secret"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(principalLookupUseCase);
    }

    @Test
    void blankConfiguredSecretFailsClosedBeforeLookup() {
        PrincipalLookupUseCase lookup = org.mockito.Mockito.mock(PrincipalLookupUseCase.class);
        PrincipalInternalController controller = new PrincipalInternalController(lookup, " ");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> controller.findByPrincipalId("service-secret", "42"))
                .isInstanceOf(com.delivery.auth_service.exception.AccessDeniedException.class);
        verifyNoInteractions(lookup);
    }
}
