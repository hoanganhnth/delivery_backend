package com.delivery.auth.application;

import com.delivery.auth.application.api.FirebaseChatTokenPort;
import com.delivery.auth.application.api.FirebaseChatTokenUseCase.Actor;
import com.delivery.auth.domain.policy.FirebaseChatUnavailable;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FirebaseChatWorkflowTest {
    @Test void rejectsDisabledOrUnstableIdentityBeforeTouchingIssuer() {
        Fake issuer = new Fake();
        assertThatThrownBy(() -> new DefaultFirebaseChatTokenUseCase(issuer, false).issue(null))
                .isInstanceOf(FirebaseChatUnavailable.class).hasMessage("Firebase support chat is disabled");
        var core = new DefaultFirebaseChatTokenUseCase(issuer, true);
        for (Long id : Arrays.asList(null, 0L, -1L)) {
            assertThatThrownBy(() -> core.issue(new Actor(id, Set.of("USER"), false)))
                    .hasMessage("Authenticated principal is missing");
        }
        assertThatThrownBy(() -> core.issue(null)).hasMessage("Authenticated principal is missing");
        assertThat(issuer.availabilityChecks).isZero();
    }

    @Test void unavailableCredentialsAndMissingRoleNeverMint() {
        Fake issuer = new Fake(); issuer.available = false;
        var core = new DefaultFirebaseChatTokenUseCase(issuer, true);
        assertThatThrownBy(() -> core.issue(new Actor(42L, Set.of("USER"), false)))
                .hasMessage("Firebase support chat credentials are unavailable");
        issuer.available = true;
        assertThatThrownBy(() -> core.issue(new Actor(42L,
                new LinkedHashSet<>(Arrays.asList(null, "", " ")), false)))
                .hasMessage("Authenticated role is missing");
        assertThat(issuer.uid).isNull();
    }

    @Test void usesPrincipalAndFirstCanonicalRoleWithExistingSupportAgentFact() {
        Fake issuer = new Fake();
        var core = new DefaultFirebaseChatTokenUseCase(issuer, true);
        var result = core.issue(new Actor(42L, new LinkedHashSet<>(Arrays.asList(null, " ", "ROLE_admin", "USER")), true));
        assertThat(result.token()).isEqualTo("custom-token");
        assertThat(result.expiresInSeconds()).isEqualTo(3_600L);
        assertThat(result.principalId()).isEqualTo(42L);
        assertThat(result.role()).isEqualTo("ADMIN");
        assertThat(issuer.uid).isEqualTo("42");
        assertThat(issuer.claims).containsExactlyInAnyOrderEntriesOf(
                Map.of("principalId", "42", "role", "ADMIN", "supportAgent", true));
        core.issue(new Actor(42L, Set.of("user"), false));
        assertThat(issuer.claims).containsEntry("role", "USER").containsEntry("supportAgent", false);
    }

    private static final class Fake implements FirebaseChatTokenPort {
        boolean available = true;
        int availabilityChecks;
        String uid;
        Map<String, Object> claims;
        public boolean available() { availabilityChecks++; return available; }
        public String issue(String uid, Map<String, Object> claims) {
            this.uid = uid; this.claims = claims; return "custom-token";
        }
    }
}
