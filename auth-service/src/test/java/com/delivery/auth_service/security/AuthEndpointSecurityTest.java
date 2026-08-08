package com.delivery.auth_service.security;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.delivery.auth_service.controller.AuthController;
import com.delivery.auth_service.controller.InternalRegistrationController;
import com.delivery.auth_service.controller.JwksController;
import com.delivery.auth_service.dto.AuthResponse;
import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.service.AuthService;
import com.delivery.auth_service.service.AccountSecurityService;
import com.delivery.auth_service.service.TokenService;
import org.springframework.web.client.RestTemplate;

import com.delivery.auth.resourceserver.security.DeliveryJwtAuthenticationConverter;

import org.springframework.security.oauth2.jwt.JwtDecoder;

@WebMvcTest(
        controllers = { AuthController.class, InternalRegistrationController.class, JwksController.class },
        properties = "app.internal.secret=service-secret",
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({ SecurityConfig.class, DeliveryJwtAuthenticationConverter.class })
class AuthEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AccountSecurityService accountSecurityService;

    @MockitoBean
    private RestTemplate restTemplate;

    @Test
    void socialLoginIsPublic() throws Exception {
        when(authService.socialLogin(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AuthResponse("access", "refresh", 1L, "user@example.com", "USER"));

        mockMvc.perform(post("/api/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "google",
                                  "token": "signed-id-token",
                                  "deviceId": "phone-1"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void registrationReturnsAuthIdentityAndOpaqueUserHandoff() throws Exception {
        AuthAccount account = new AuthAccount();
        org.springframework.test.util.ReflectionTestUtils.setField(account, "id", 11L);
        account.setEmail("user@example.com");
        account.setRole(AuthAccount.Role.USER);
        account.setIsActive(true);
        when(authService.register(org.mockito.ArgumentMatchers.any())).thenReturn(account);
        when(accountSecurityService.issueUserProvisioning(
                org.mockito.ArgumentMatchers.eq(account),
                org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn("opaque-handoff");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com",
                                 "password":"Password1!",
                                 "role":"USER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.authId").value(11))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.email").value("user@example.com"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.role").value("USER"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.provisioningToken").value("opaque-handoff"));

        verify(accountSecurityService).requestEmailVerification(
                org.mockito.ArgumentMatchers.eq("user@example.com"),
                org.mockito.ArgumentMatchers.nullable(String.class));
    }

    @Test
    void internalRegistrationEndpointsRequireTheServiceCredential() throws Exception {
        when(accountSecurityService.resolveUserProvisioning("opaque-handoff"))
                .thenReturn(new com.delivery.auth_service.dto.UserProvisioningIdentityResponse(
                        11L, "user@example.com", "USER"));

        mockMvc.perform(post("/api/auth/internal/registrations/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provisioningToken\":\"opaque-handoff\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/internal/registrations/resolve")
                        .header("Internal-Token", "service-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provisioningToken\":\"opaque-handoff\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetAndVerificationEndpointsArePublicPostOnly() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/auth/email-verification/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"abcdefghijklmnopqrstuvwxyzABCDEFGH",
                                 "newPassword":"ChangedPassword1!"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abcdefghijklmnopqrstuvwxyzABCDEFGH\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/forgot-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPasswordResponseDoesNotRevealWhetherEmailExists() throws Exception {
        String existing = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"known@example.com\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(missing).isEqualTo(existing);
    }

    @Test
    void sessionsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/sessions"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/auth/sessions/phone-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwksIsPublicReadOnlyAndUsesStandardCacheHeader() throws Exception {
        when(tokenService.getJwks()).thenReturn(java.util.Map.of("keys", java.util.List.of(
                java.util.Map.of("kty", "RSA", "alg", "RS256", "use", "sig",
                        "kid", "auth-key-1", "n", "modulus", "e", "AQAB"))));

        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Cache-Control", "max-age=300"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.keys[0].kid").value("auth-key-1"));

        mockMvc.perform(post("/.well-known/jwks.json"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedAccountCanRevokeOneDeviceSession() throws Exception {
        mockMvc.perform(delete("/api/auth/sessions/phone-1")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isOk());

        verify(authService).revokeDeviceSession("user@example.com", "phone-1");
    }

    @Test
    void adminRoutesRejectNonAdminUsers() throws Exception {
        mockMvc.perform(get("/api/auth/accounts/42")
                        .with(user("customer@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void removedAdminAccountListIsNotMapped() throws Exception {
        mockMvc.perform(get("/api/auth/admin/accounts")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void removedInternalEmailLookupIsNotPublic() throws Exception {
        mockMvc.perform(get("/api/auth/accounts/email/user@example.com"))
                .andExpect(status().isUnauthorized());
    }
}
