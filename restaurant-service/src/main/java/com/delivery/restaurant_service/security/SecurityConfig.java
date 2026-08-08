package com.delivery.restaurant_service.security;

import com.delivery.auth.resourceserver.security.DeliveryJwtAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, DeliveryJwtAuthenticationConverter converter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/restaurants",
                                "/api/restaurants/search",
                                "/api/restaurants/*",
                                "/api/restaurants/*/ratings").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/menu-items/restaurant/*",
                                "/api/menu-items/restaurant/*/available").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/restaurants/validate/order").permitAll()
                        .requestMatchers("/api/restaurants/internal/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                );
        return http.build();
    }
}
