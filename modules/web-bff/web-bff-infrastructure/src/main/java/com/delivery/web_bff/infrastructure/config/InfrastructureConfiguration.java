package com.delivery.web_bff.infrastructure.config;
import com.delivery.web_bff.infrastructure.session.*; import com.delivery.web_bff.application.api.Ports; import java.time.Clock; import java.util.Base64; import org.springframework.beans.factory.annotation.Value; import org.springframework.context.annotation.*; import org.springframework.web.client.RestClient;
@Configuration public class InfrastructureConfiguration {
 @Bean public Clock webBffClock(){return Clock.systemUTC();}
 @Bean public Ports.TokenProtection webBffTokenProtection(TokenVault vault){return vault;}
 @Bean public RestClient authRestClient(@Value("${web-bff.gateway-base-url}") String url){return RestClient.builder().baseUrl(url).build();}
 @Bean public RestClient gatewayRestClient(@Value("${web-bff.gateway-base-url}") String url){return RestClient.builder().baseUrl(url).build();}
 @Bean public TokenVault infrastructureTokenVault(@Value("${web-bff.encryption-key-version}") String version,@Value("${web-bff.encryption-key-base64}") String encoded){if(encoded==null||encoded.isBlank())throw new IllegalStateException("WEB_BFF_ENCRYPTION_KEY_BASE64 is required");return new TokenVault(version,Base64.getDecoder().decode(encoded),new java.security.SecureRandom());}
}
