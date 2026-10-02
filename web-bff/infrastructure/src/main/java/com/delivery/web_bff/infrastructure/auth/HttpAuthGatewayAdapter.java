package com.delivery.web_bff.infrastructure.auth;
import com.delivery.web_bff.application.api.Ports; import com.delivery.web_bff.domain.session.AuthenticationRejectedException; import java.util.Map; import org.springframework.beans.factory.annotation.Qualifier; import org.springframework.stereotype.Component; import org.springframework.web.client.HttpClientErrorException; import org.springframework.web.client.RestClient;
@Component
public final class HttpAuthGatewayAdapter implements Ports.Authentication {
 private final RestClient client; public HttpAuthGatewayAdapter(@Qualifier("authRestClient") RestClient authRestClient){client=authRestClient;}
 public Ports.AuthenticatedTokens login(Ports.LoginCommand c){try{return tokens(client.post().uri("/api/auth/login").body(loginBody(c)).retrieve().body(Envelope.class),"login");}catch(HttpClientErrorException.Unauthorized e){throw new AuthenticationRejectedException("Invalid email or password");}}
 public Ports.AuthenticatedTokens refresh(String token){return tokens(client.post().uri("/api/auth/refresh-token").body(Map.of("refreshToken",token)).retrieve().body(Envelope.class),"refresh");}
 public void logout(String token){client.post().uri("/api/auth/logout").body(Map.of("refreshToken",token)).retrieve().toBodilessEntity();}
 private Map<String, Object> loginBody(Ports.LoginCommand command) {
     Map<String, Object> body = new java.util.LinkedHashMap<>();
     body.put("email", command.email()); body.put("password", command.password()); body.put("role", command.role());
     body.put("deviceId", command.deviceId()); body.put("deviceName", command.deviceName()); body.put("deviceType", "WEB");
     return body;
 }
 private Ports.AuthenticatedTokens tokens(Envelope e,String op){if(e==null||e.status()!=1||e.data()==null||blank(e.data().accessToken())||blank(e.data().refreshToken())||e.data().authId()==null||blank(e.data().email())||blank(e.data().role()))throw new IllegalStateException("Auth "+op+" returned an invalid response");return new Ports.AuthenticatedTokens(e.data().accessToken(),e.data().refreshToken(),e.data().authId(),e.data().email(),e.data().role());}
 private boolean blank(String s){return s==null||s.isBlank();} private record Envelope(int status,String message,Data data){} private record Data(String accessToken,String refreshToken,Long authId,String email,String role){}
}
