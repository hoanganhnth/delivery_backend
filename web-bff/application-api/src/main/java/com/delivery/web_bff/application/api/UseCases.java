package com.delivery.web_bff.application.api;

import com.delivery.web_bff.domain.session.SessionMaterial;
import java.util.Optional;

public interface UseCases {
    public interface Login { SessionMaterial execute(Ports.LoginCommand command); }
    public interface CurrentSession { Optional<SessionView> execute(String rawSessionId); }
    public interface Logout { void execute(String rawSessionId, String csrfToken); }
    public interface Refresh { SessionView execute(String rawSessionId, String csrfToken); }
    public interface AccessTokenResolution { String execute(String rawSessionId, String csrfToken, boolean mutation); }
    public interface ApiPolicyEvaluation { boolean execute(Ports.HttpVerb method, String path); }
    public interface ProxyForwarding { Ports.ForwardedResponse execute(ProxyRequest request); }
    public record SessionView(long principalId, String email, String role, long generation) { }
    public record ProxyRequest(Ports.HttpVerb method, String path, String query, java.util.Map<String, java.util.List<String>> headers,
            byte[] body, String rawSessionId, String csrfToken) { }
}
