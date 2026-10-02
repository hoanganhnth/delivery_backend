package com.delivery.web_bff.session;

import com.delivery.web_bff.application.api.Ports;
import com.delivery.web_bff.application.api.UseCases;
import com.delivery.web_bff.domain.session.SessionMaterial;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bff/session")
public class WebSessionController {
    public static final String COOKIE = "__Host-delivery-session";
    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    private final long sessionMaxAgeSeconds;
    private final UseCases.Login login;
    private final UseCases.CurrentSession current;
    private final UseCases.Refresh refresh;
    private final UseCases.Logout logout;

    public WebSessionController(UseCases.Login login, UseCases.CurrentSession current,
            UseCases.Refresh refresh, UseCases.Logout logout,
            @Value("${web-bff.session-max-age-seconds}") long sessionMaxAgeSeconds) {
        this.login = login;
        this.current = current;
        this.refresh = refresh;
        this.logout = logout;
        if (sessionMaxAgeSeconds <= 0) throw new IllegalArgumentException("BFF session max age must be positive");
        this.sessionMaxAgeSeconds = sessionMaxAgeSeconds;
    }

    @PostMapping("/refresh")
    public SessionView refresh(
            @CookieValue(name = COOKIE, required = false) String rawSessionId,
            @RequestHeader(name = "X-CSRF-Token", required = false) String csrf,
            HttpServletResponse response) {
        preventCaching(response);
        return SessionView.authenticated(refresh.execute(rawSessionId, csrf), null);
    }

    @PostMapping("/login")
    public ResponseEntity<SessionView> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        preventCaching(response);
        SessionMaterial material = login.execute(new Ports.LoginCommand(request.email(), request.password(), request.role(),
                request.deviceName(), "web-bff-" + java.util.UUID.randomUUID()));
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(material.rawSessionId(), sessionMaxAgeSeconds).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie(material.rawCsrfToken(), sessionMaxAgeSeconds).toString());
        return ResponseEntity.ok(SessionView.authenticated(material.session(), material.rawCsrfToken()));
    }

    @GetMapping
    public SessionView current(@CookieValue(name = COOKIE, required = false) String rawSessionId,
            HttpServletResponse response) {
        preventCaching(response);
        return current.execute(rawSessionId)
                .map(session -> SessionView.authenticated(session, null))
                .orElseGet(SessionView::anonymous);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = COOKIE, required = false) String rawSessionId,
            @RequestHeader(name = "X-CSRF-Token", required = false) String csrf,
            HttpServletResponse response) {
        preventCaching(response);
        logout.execute(rawSessionId, csrf);
        // Do not emit an expiring Set-Cookie here: a slow logout response could
        // otherwise erase a newer login completed in another tab. Server-side
        // revocation is authoritative and the next login rotates both cookies.
        return ResponseEntity.noContent().build();
    }

    private ResponseCookie sessionCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(true).sameSite("Lax")
                .path("/").maxAge(maxAgeSeconds).build();
    }

    private ResponseCookie csrfCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(CSRF_COOKIE, value).httpOnly(false).secure(true).sameSite("Lax")
                .path("/").maxAge(maxAgeSeconds).build();
    }

    private void preventCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password, @NotBlank String role,
            String deviceName) {
        public LoginRequest {
            if (deviceName == null || deviceName.isBlank()) deviceName = "Delivery Web";
        }
    }

    public record SessionView(boolean authenticated, Long principalId, String email, String role,
            long sessionVersion, String csrfToken) {
        static SessionView authenticated(UseCases.SessionView session, String csrf) {
            return new SessionView(true, session.principalId(), session.email(), session.role(),
                    session.generation(), csrf);
        }
        static SessionView authenticated(SessionMaterial material, String csrf) {
            var session = material.session();
            return new SessionView(true, session.principalId(), session.email(), session.role(),
                    session.generation(), csrf);
        }
        static SessionView authenticated(com.delivery.web_bff.domain.session.WebSession session, String csrf) {
            return new SessionView(true, session.principalId(), session.email(), session.role(),
                    session.generation(), csrf);
        }
        static SessionView anonymous() { return new SessionView(false, null, null, null, 0, null); }
    }
}
