package com.delivery.auth.resourceserver.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryJwtAuthenticationConverterTest {

    @Test
    void mapsEveryCanonicalRoleIntoTheActorAndAuthorities() {
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .header("kid", "auth-key-current")
                .subject("42")
                .claim("principal_id", 9001)
                .claim("legacy_user_id", 42)
                .claim("identity_claims_version", 1)
                .claim("email", "multi-role@example.test")
                .claim("roles", List.of("USER", "SHIPPER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        AuthenticatedActorAuthenticationToken token = (AuthenticatedActorAuthenticationToken)
                new DeliveryJwtAuthenticationConverter().convert(jwt);
        AuthenticatedActor actor = (AuthenticatedActor) token.getPrincipal();

        assertThat(actor.getPrincipalId()).isEqualTo(9001L);
        assertThat(actor.getUserId()).isEqualTo(42L);
        assertThat(actor.getRoles()).containsExactlyInAnyOrder("USER", "SHIPPER");
        assertThat(token.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_SHIPPER");
    }

    @Test
    void rejectsLegacyOnlyTokenRatherThanTreatingSubjectAsPrincipal() {
        Jwt jwt = Jwt.withTokenValue("legacy-access-token")
                .header("alg", "RS256")
                .subject("42")
                .claim("email", "legacy@example.test")
                .claim("roles", List.of("USER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        assertThatThrownBy(() -> new DeliveryJwtAuthenticationConverter().convert(jwt))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                .hasMessageContaining("required identity claims");
    }

    @Test
    void mapsServerSignedSimulationContextIntoTheActor() {
        UUID runId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("simulation-access-token")
                .header("alg", "RS256")
                .subject("42")
                .claim("principal_id", 9001)
                .claim("legacy_user_id", 42)
                .claim("identity_claims_version", 1)
                .claim("email", "virtual-shipper@example.test")
                .claim("roles", List.of("SHIPPER"))
                .claim("simulation_mode", "SIMULATION")
                .claim("simulation_run_id", runId.toString())
                .claim("simulation_cohort_id", cohortId.toString())
                .claim("simulation_binding_version", 3)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        var token = (AuthenticatedActorAuthenticationToken)
                new DeliveryJwtAuthenticationConverter().convert(jwt);
        AuthenticatedActor actor = (AuthenticatedActor) token.getPrincipal();

        assertThat(actor.isSimulationActor()).isTrue();
        assertThat(actor.getSimulationContext().runId()).isEqualTo(runId);
        assertThat(actor.getSimulationContext().cohortId()).isEqualTo(cohortId);
        assertThat(actor.getSimulationContext().bindingVersion()).isEqualTo(3L);
    }

    @Test
    void rejectsIncompleteSimulationClaims() {
        Jwt jwt = Jwt.withTokenValue("invalid-simulation-access-token")
                .header("alg", "RS256")
                .subject("42")
                .claim("principal_id", 9001)
                .claim("legacy_user_id", 42)
                .claim("identity_claims_version", 1)
                .claim("email", "virtual-shipper@example.test")
                .claim("roles", List.of("SHIPPER"))
                .claim("simulation_mode", "SIMULATION")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        assertThatThrownBy(() -> new DeliveryJwtAuthenticationConverter().convert(jwt))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                .hasMessageContaining("simulation");
    }
}
