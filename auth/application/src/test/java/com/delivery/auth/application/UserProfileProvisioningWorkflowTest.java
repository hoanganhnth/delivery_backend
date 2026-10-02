package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UserProfileProvisioningWorkflowTest {
    @Test void acceptsOnlyTheMatchingProfileAndAdvancesLifecycleWithoutLosingAccountFacts() {
        var account=account();
        var core=new DefaultUserProfileProvisioningUseCase(a -> {
            assertThat(a).isSameAs(account);
            return new UserProfileProvisioningReply(1,"ok",11L,7L,"USER@example.com","USER");
        });
        var linked=core.provision(account);
        assertThat(linked.userId()).isEqualTo(11L);
        assertThat(linked.lifecycleStatus()).isEqualTo(AuthAccount.LifecycleStatus.ACTIVE);
        assertThat(linked.lifecycleVersion()).isEqualTo(1L);
        assertThat(linked.passwordHash()).isEqualTo(account.passwordHash());
        assertThat(linked.emailVerificationRequired()).isEqualTo(account.emailVerificationRequired());
        assertThat(account.userId()).isNull();
    }
    @Test void emptyRejectedOrIncompleteReplyCannotBindTheAccount() {
        for (UserProfileProvisioningReply reply : new UserProfileProvisioningReply[]{null,
                new UserProfileProvisioningReply(0,"rejected",11L,7L,"user@example.com","USER"),
                new UserProfileProvisioningReply(1,"missing profile",null,7L,"user@example.com","USER")}) {
            assertThatThrownBy(() -> new DefaultUserProfileProvisioningUseCase(a -> reply).provision(account()))
                    .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("User service did not provision profile:");
        }
    }
    @Test void rejectsConflictingPrincipalEmailAndRole() {
        for (UserProfileProvisioningReply reply : new UserProfileProvisioningReply[]{
                new UserProfileProvisioningReply(1,"ok",11L,null,"user@example.com","USER"),
                new UserProfileProvisioningReply(1,"ok",11L,8L,"user@example.com","USER"),
                new UserProfileProvisioningReply(1,"ok",11L,7L,null,"USER"),
                new UserProfileProvisioningReply(1,"ok",11L,7L,"other@example.com","USER"),
                new UserProfileProvisioningReply(1,"ok",11L,7L,"user@example.com","ADMIN")}) {
            assertThatThrownBy(() -> new DefaultUserProfileProvisioningUseCase(a -> reply).provision(account()))
                    .hasMessage("User service returned a conflicting provisioning identity");
        }
    }
    static AuthAccount account() {
        return new AuthAccount(7L,null,AuthAccount.LifecycleStatus.PENDING_PROFILE,0L,"user@example.com","hash",
                AuthAccount.Role.USER,true,true,null,false,0L,null,null,0,null,false,null,null,0L,null,null,null);
    }
}
