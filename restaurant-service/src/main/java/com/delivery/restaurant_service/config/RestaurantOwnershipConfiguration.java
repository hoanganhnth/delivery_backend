package com.delivery.restaurant_service.config;

import com.delivery.identity.client.HttpIdentityPrincipalClient;
import com.delivery.identity.client.IdentityPrincipalClient;
import com.delivery.platform.http.blocking.RestTemplateBlockingHttpExchange;
import com.delivery.restaurant.application.DefaultRestaurantOwnerAssignmentUseCase;
import com.delivery.restaurant.application.api.PrincipalOwnershipDirectory;
import com.delivery.restaurant.application.api.RestaurantOwnerAssignmentUseCase;
import com.delivery.restaurant.domain.catalog.MenuItemLifecyclePolicy;
import com.delivery.restaurant.domain.catalog.RestaurantLifecyclePolicy;
import com.delivery.restaurant.infrastructure.identity.IdentityPrincipalDirectoryAdapter;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Clock;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class RestaurantOwnershipConfiguration {

    @Bean
    @Lazy
    IdentityPrincipalClient identityPrincipalClient(
            RestTemplateBuilder builder,
            IdentityClientProperties properties,
            @Value("${app.internal.secret:}") String internalSecret) {
        Duration connectTimeout = Duration.ofMillis(properties.getConnectTimeoutMs());
        Duration readTimeout = Duration.ofMillis(properties.getReadTimeoutMs());
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var exchange = new RestTemplateBlockingHttpExchange(
                builder.build(), httpClient, readTimeout);
        return new HttpIdentityPrincipalClient(
                exchange, URI.create(properties.getBaseUrl()), internalSecret);
    }

    @Bean
    PrincipalOwnershipDirectory principalOwnershipDirectory(
            ObjectProvider<IdentityPrincipalClient> clientProvider) {
        Supplier<IdentityPrincipalClient> lazyClient = clientProvider::getObject;
        return new IdentityPrincipalDirectoryAdapter(lazyClient);
    }

    @Bean
    RestaurantOwnerAssignmentUseCase restaurantOwnerAssignmentUseCase(
            PrincipalOwnershipDirectory principalDirectory) {
        return new DefaultRestaurantOwnerAssignmentUseCase(principalDirectory);
    }

    @Bean
    RestaurantLifecyclePolicy restaurantLifecyclePolicy() {
        return new RestaurantLifecyclePolicy();
    }

    @Bean
    MenuItemLifecyclePolicy menuItemLifecyclePolicy() {
        return new MenuItemLifecyclePolicy();
    }

    @Bean
    Clock catalogClock() {
        return Clock.systemUTC();
    }
}
