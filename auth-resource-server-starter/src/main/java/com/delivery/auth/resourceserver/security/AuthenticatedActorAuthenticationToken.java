package com.delivery.auth.resourceserver.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

public class AuthenticatedActorAuthenticationToken extends AbstractAuthenticationToken {
    private static final long serialVersionUID = 1L;

    private final Jwt jwt;
    private final AuthenticatedActor actor;

    public AuthenticatedActorAuthenticationToken(Jwt jwt, AuthenticatedActor actor, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.jwt = jwt;
        this.actor = actor;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return jwt.getTokenValue();
    }

    @Override
    public String getName() {
        if (actor.getEmail() != null && !actor.getEmail().isBlank()) {
            return actor.getEmail();
        }
        if (actor.getUserId() != null) {
            return actor.getUserId().toString();
        }
        return super.getName();
    }

    @Override
    public Object getPrincipal() {
        return actor;
    }

    public Jwt getJwt() {
        return jwt;
    }
}
