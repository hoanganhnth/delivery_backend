package com.delivery.web_bff.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.delivery.web_bff.application.api.Ports;
import com.delivery.web_bff.application.api.UseCases;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class WebSessionControllerTest {
    private final UseCases.Login login = mock(UseCases.Login.class);
    private final UseCases.CurrentSession current = mock(UseCases.CurrentSession.class);
    private final UseCases.Refresh refresh = mock(UseCases.Refresh.class);
    private final UseCases.Logout logout = mock(UseCases.Logout.class);
    private final com.delivery.web_bff.infrastructure.session.TokenVault vault = new com.delivery.web_bff.infrastructure.session.TokenVault("v1",
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), new SecureRandom());
    private final com.delivery.web_bff.domain.session.SessionFactory factory = new com.delivery.web_bff.domain.session.SessionFactory(
            vault, size -> { byte[] bytes = new byte[size]; new SecureRandom().nextBytes(bytes); return bytes; }, Duration.ofDays(7));
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new WebSessionController(login, current, refresh, logout, 604800))
                .setControllerAdvice(new WebSessionExceptionHandler()).build();
    }

    @Test
    void loginSetsHttpOnlySessionAndReadableCsrfCookiesWithoutBearerTokens() throws Exception {
        com.delivery.web_bff.domain.session.SessionMaterial material = factory.create("access-secret", "refresh-secret", 42L,
                "user@example.com", "USER", Instant.EPOCH);
        when(login.execute(any(Ports.LoginCommand.class))).thenReturn(material);

        var result = mvc.perform(post("/bff/session/login")
                .header("Origin", "https://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"password\",\"role\":\"USER\",\"deviceName\":\"Browser\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .contains("__Host-delivery-session=")
                .contains("HttpOnly").contains("Secure").contains("SameSite=Lax").contains("Path=/"));
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .contains("XSRF-TOKEN=").doesNotContain("HttpOnly")
                .contains("Secure").contains("SameSite=Lax").contains("Path=/"));
        assertThat(result.getResponse().getContentAsString()).doesNotContain("access-secret").doesNotContain("refresh-secret");
    }

    @Test
    void rejectedCredentialsReturnStableUnauthorizedEnvelope() throws Exception {
        when(login.execute(any(Ports.LoginCommand.class)))
                .thenThrow(new UseCases.AuthenticationRejectedException("Invalid email or password"));

        mvc.perform(post("/bff/session/login")
                .header("Origin", "https://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"wrong-password\",\"role\":\"USER\",\"deviceName\":\"Browser\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REJECTED"))
                .andExpect(jsonPath("$.error.message").value("Invalid email or password"));
    }

    @Test
    void anonymousSessionDoesNotRequireOrExposeAnySecret() throws Exception {
        when(current.execute(null)).thenReturn(Optional.empty());

        mvc.perform(get("/bff/session"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .isEqualTo("{\"authenticated\":false,\"principalId\":null,\"email\":null,\"role\":null,\"sessionVersion\":0,\"csrfToken\":null}"));
    }

    @Test
    void logoutRequiresCsrfAtServiceBoundaryWithoutClobberingANewerTabLogin() throws Exception {
        org.mockito.Mockito.doNothing().when(logout).execute("session", "csrf");

        var result = mvc.perform(post("/bff/session/logout")
                .cookie(new jakarta.servlet.http.Cookie(WebSessionController.COOKIE, "session"))
                .header("Origin", "https://localhost:5173")
                .header("X-CSRF-Token", "csrf"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        verify(logout).execute("session", "csrf");
        assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
    }
}
