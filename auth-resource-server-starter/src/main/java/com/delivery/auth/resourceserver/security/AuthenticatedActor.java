package com.delivery.auth.resourceserver.security;

import com.delivery.identity.contracts.SimulationContext;
import java.io.Serializable;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class AuthenticatedActor implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Stable authentication/authorization identity: auth_account.id. */
    private final Long principalId;
    /** Temporary profile identity retained only while ownership data migrates. */
    private final Long legacyUserId;
    private final String email;
    private final Set<String> roles;
    private final SimulationContext simulationContext;

    public AuthenticatedActor(Long userId, String email, Set<String> roles) {
        this(userId, userId, email, roles, SimulationContext.real());
    }

    public AuthenticatedActor(Long principalId, Long legacyUserId, String email, Set<String> roles) {
        this(principalId, legacyUserId, email, roles, SimulationContext.real());
    }

    public AuthenticatedActor(Long principalId, Long legacyUserId, String email, Set<String> roles,
                              SimulationContext simulationContext) {
        this.principalId = principalId;
        this.legacyUserId = legacyUserId;
        this.email = email;
        this.roles = roles != null ? Collections.unmodifiableSet(roles) : Collections.emptySet();
        this.simulationContext = SimulationContext.orReal(simulationContext);
        this.simulationContext.requireValid();
    }

    public Long getUserId() {
        return legacyUserId;
    }

    public Long getPrincipalId() { return principalId; }

    public Long getLegacyUserId() { return legacyUserId; }

    public String getEmail() {
        return email;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public SimulationContext getSimulationContext() { return simulationContext; }

    public boolean isSimulationActor() { return simulationContext.isSimulation(); }

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
        return Objects.equals(principalId, that.principalId) &&
               Objects.equals(legacyUserId, that.legacyUserId) &&
               Objects.equals(email, that.email) &&
               Objects.equals(roles, that.roles) &&
               Objects.equals(simulationContext, that.simulationContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(principalId, legacyUserId, email, roles, simulationContext);
    }

    @Override
    public String toString() {
        return "AuthenticatedActor{" +
                "principalId=" + principalId +
                ", legacyUserId=" + legacyUserId +
                ", email='" + email + '\'' +
                ", roles=" + roles +
                ", simulationContext=" + simulationContext +
                '}';
    }
}
