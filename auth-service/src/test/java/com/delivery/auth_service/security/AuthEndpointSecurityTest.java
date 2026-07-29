package com.delivery.auth_service.security;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.delivery.auth_service.controller.AuthController;
import com.delivery.auth_service.dto.AuthResponse;
import com.delivery.auth_service.service.AuthService;
import com.delivery.auth_service.service.TokenService;

@WebMvcTest(
        controllers = AuthController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class })
class AuthEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private TokenService tokenService;

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
    void sessionsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/sessions"))
                .andExpect(status().isUnauthorized());
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
