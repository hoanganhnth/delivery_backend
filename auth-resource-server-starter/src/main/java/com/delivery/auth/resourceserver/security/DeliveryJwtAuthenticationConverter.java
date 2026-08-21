package com.delivery.auth.resourceserver.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;
import java.util.stream.Collectors;

public class DeliveryJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long legacyUserId = numeric(jwt.getClaim("legacy_user_id"));
        Long principalId = numeric(jwt.getClaim("principal_id"));
        Long identityClaimsVersion = numeric(jwt.getClaim("identity_claims_version"));

        /*
         * `sub` remains the legacy user profile during dual-claim rollout, but
         * it is never a substitute for an explicit stable principal. Treating
         * a legacy profile ID as auth_account.id would silently authorize the
         * wrong aggregate after the IDs diverge. The JWKS migration has no
         * legacy-token fallback: every access token carries both claims.
         */
        if (principalId == null || legacyUserId == null || identityClaimsVersion == null) {
            throw new BadCredentialsException("Access token is missing required identity claims");
        }
        if (identityClaimsVersion != 1L) {
            throw new BadCredentialsException("Unsupported access token identity claims version");
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

        AuthenticatedActor actor = new AuthenticatedActor(principalId, legacyUserId, email, rolesSet);

        Set<GrantedAuthority> authorities = rolesSet.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .map(r -> r.startsWith("ROLE_") ? r.toUpperCase() : "ROLE_" + r.toUpperCase())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        return new AuthenticatedActorAuthenticationToken(jwt, actor, authorities);
    }

    private Long numeric(Object value) {
        if (value instanceof Number number) {
            long parsed = number.longValue();
            return parsed > 0 ? parsed : null;
        }
        if (!(value instanceof String string) || !string.matches("\\d+")) return null;
        try {
            long parsed = Long.parseLong(string);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
