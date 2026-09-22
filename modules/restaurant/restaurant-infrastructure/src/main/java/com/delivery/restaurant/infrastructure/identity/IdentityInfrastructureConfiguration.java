package com.delivery.restaurant.infrastructure.identity;

import com.delivery.identity.client.HttpIdentityPrincipalClient;
import com.delivery.identity.client.IdentityPrincipalClient;
import com.delivery.platform.http.blocking.RestTemplateBlockingHttpExchange;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;

@Configuration
@EnableConfigurationProperties(IdentityClientProperties.class)
public class IdentityInfrastructureConfiguration {

    @Bean
    @Lazy
    IdentityPrincipalClient identityPrincipalClient(
            RestTemplateBuilder builder,
            IdentityClientProperties properties,
            @Value("${app.internal.secret:}") String internalSecret) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var exchange = new RestTemplateBlockingHttpExchange(
                builder.build(), httpClient, Duration.ofMillis(properties.getReadTimeoutMs()));
        return new HttpIdentityPrincipalClient(
                exchange, URI.create(properties.getBaseUrl()), internalSecret);
    }

    @Bean
    com.delivery.restaurant.application.api.PrincipalOwnershipDirectory principalOwnershipDirectory(
            ObjectProvider<IdentityPrincipalClient> clientProvider) {
        Supplier<IdentityPrincipalClient> lazyClient = clientProvider::getObject;
        return new IdentityPrincipalDirectoryAdapter(lazyClient);
    }
}
