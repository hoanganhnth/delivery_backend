package com.delivery.web_bff.proxy;

import com.delivery.web_bff.application.api.Ports;
import com.delivery.web_bff.application.api.UseCases;
import com.delivery.web_bff.session.WebSessionController;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiProxyController {
    private final UseCases.ProxyForwarding proxy;

    public ApiProxyController(UseCases.ProxyForwarding proxy) { this.proxy = proxy; }

    @RequestMapping("/bff/api/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,
            @RequestHeader HttpHeaders headers,
            @CookieValue(name = WebSessionController.COOKIE, required = false) String rawSessionId,
            @RequestHeader(name = "X-CSRF-Token", required = false) String csrfToken) throws IOException {
        String path = request.getRequestURI().substring("/bff".length());
        Ports.HttpVerb method = Ports.HttpVerb.valueOf(request.getMethod());
        Ports.ForwardedResponse forwarded = proxy.execute(new UseCases.ProxyRequest(method, path,
                request.getQueryString(), headers, request.getInputStream().readAllBytes(), rawSessionId, csrfToken));
        ResponseEntity.BodyBuilder response = ResponseEntity.status(forwarded.status());
        forwarded.headers().forEach((name, values) -> response.header(name, values.toArray(String[]::new)));
        return response.body(forwarded.body());
    }
}
