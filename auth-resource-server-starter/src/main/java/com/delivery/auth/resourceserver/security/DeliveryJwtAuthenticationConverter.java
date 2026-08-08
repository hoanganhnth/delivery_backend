package com.delivery.auth.resourceserver.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;
import java.util.stream.Collectors;

public class DeliveryJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String subject = jwt.getSubject();
        Long userId = null;
        if (subject != null && subject.matches("\\d+")) {
            try {
                userId = Long.parseLong(subject);
            } catch (NumberFormatException ignored) {
            }
        }

        String email = jwt.getClaimAsString("email");

        Set<String> rolesSet = new LinkedHashSet<>();
        List<String> rolesClaim = jwt.getClaimAsStringList("roles");
        if (rolesClaim != null && !rolesClaim.isEmpty()) {
            rolesSet.addAll(rolesClaim);
        } else {
            String roleClaim = jwt.getClaimAsString("role");
            if (roleClaim != null && !roleClaim.isBlank()) {
                rolesSet.add(roleClaim);
            }
        }

        AuthenticatedActor actor = new AuthenticatedActor(userId, email, rolesSet);

        Set<GrantedAuthority> authorities = rolesSet.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .map(r -> r.startsWith("ROLE_") ? r.toUpperCase() : "ROLE_" + r.toUpperCase())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        return new AuthenticatedActorAuthenticationToken(jwt, actor, authorities);
    }
}
