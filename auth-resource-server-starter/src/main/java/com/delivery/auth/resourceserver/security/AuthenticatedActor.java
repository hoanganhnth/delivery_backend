package com.delivery.auth.resourceserver.security;

import java.io.Serializable;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class AuthenticatedActor implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String email;
    private final Set<String> roles;

    public AuthenticatedActor(Long userId, String email, Set<String> roles) {
        this.userId = userId;
        this.email = email;
        this.roles = roles != null ? Collections.unmodifiableSet(roles) : Collections.emptySet();
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String targetRole = role.startsWith("ROLE_") ? role.substring(5) : role;
        return roles.stream().anyMatch(r -> {
            String cleanRole = r.startsWith("ROLE_") ? r.substring(5) : r;
            return cleanRole.equalsIgnoreCase(targetRole);
        });
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean isShipper() {
        return hasRole("SHIPPER");
    }

    public boolean isShopOwner() {
        return hasRole("SHOP_OWNER");
    }

    public boolean isUser() {
        return hasRole("USER");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthenticatedActor that = (AuthenticatedActor) o;
        return Objects.equals(userId, that.userId) &&
               Objects.equals(email, that.email) &&
               Objects.equals(roles, that.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, email, roles);
    }

    @Override
    public String toString() {
        return "AuthenticatedActor{" +
                "userId=" + userId +
                ", email='" + email + '\'' +
                ", roles=" + roles +
                '}';
    }
}
