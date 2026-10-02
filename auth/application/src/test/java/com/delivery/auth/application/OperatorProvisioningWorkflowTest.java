package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OperatorProvisioningWorkflowTest {
    @Test void createsVerifiedOperatorIdentityBeforeProfileHandoffAndResumesTheSameIdentity() {
        Fake f=new Fake();var core=f.core();
        assertThat(core.provisionAdmin(" ADMIN@example.com ","secret").role()).isEqualTo(AuthAccount.Role.ADMIN);
        assertThat(f.email).isEqualTo("admin@example.com");assertThat(f.created.emailVerificationRequired()).isFalse();
        assertThat(f.created.emailVerifiedAt()).isNotNull();assertThat(f.order).containsExactly("create","profile","save");
        f.existing=f.created;f.order.clear();core.provisionAdmin("admin@example.com","secret");
        assertThat(f.order).containsExactly("profile","save");
        f.existing=f.created.withProvisionedProfile(11L);f.order.clear();core.provisionAdmin("admin@example.com","secret");
        assertThat(f.order).isEmpty();
        f.existing=null;assertThat(core.provisionShipper("shipper@example.com","secret").role()).isEqualTo(AuthAccount.Role.SHIPPER);
    }
    @Test void checksConcurrentWinnerPasswordRoleAndActiveStateBeforeHandoff() {
        Fake f=new Fake();f.core().provisionShipper("shipper@example.com","secret");f.winner=f.created;
        f.created=null;f.order.clear();f.core().provisionShipper("shipper@example.com","secret");
        assertThat(f.order).containsExactly("create","profile","save");
        f.matches=false;assertThatThrownBy(() -> f.core().provisionShipper("shipper@example.com","wrong")).hasMessage("Email already registered: shipper@example.com");
        f.matches=true;assertThatThrownBy(() -> f.core().provisionAdmin("shipper@example.com","secret")).hasMessage("Email already registered: shipper@example.com");
        for(Boolean active:new Boolean[]{null,false}) {
            var a=f.winner;f.existing=new AuthAccount(a.id(),a.userId(),a.lifecycleStatus(),a.lifecycleVersion(),a.email(),a.passwordHash(),a.role(),active,a.emailVerificationRequired(),a.emailVerifiedAt(),false,0L,null,null,0,null,false,null,null,0L,null,null,null);
            assertThatThrownBy(() -> f.core().provisionShipper("shipper@example.com","secret")).hasMessage("Account is blocked or inactive");
        }
    }
    @Test void rejectsMissingCredentialsWithTheExistingOperatorMessages() {
        Fake f=new Fake();
        for(String value:new String[]{null," "}) {
            assertThatThrownBy(() -> f.core().provisionAdmin(value,"secret")).hasMessage("Operator-provisioned admin email is required");
            assertThatThrownBy(() -> f.core().provisionAdmin("admin@example.com",value)).hasMessage("Operator-provisioned admin password is required");
            assertThatThrownBy(() -> f.core().provisionShipper(value,"secret")).hasMessage("Operator-provisioned email is required");
            assertThatThrownBy(() -> f.core().provisionShipper("shipper@example.com",value)).hasMessage("Operator-provisioned password is required");
        }
        assertThat(f.order).isEmpty();
    }
    static final class Fake implements AuthAccountPort,PasswordCredentialPort,UserProfileProvisioningUseCase {
        AuthAccount existing,winner,created;String email;boolean matches=true;List<String> order=new ArrayList<>();
        DefaultOperatorProvisioningUseCase core(){return new DefaultOperatorProvisioningUseCase(this,this,this);}
        public Optional<AuthAccount> findByEmail(String e){email=e;return Optional.ofNullable(existing);}
        public Optional<AuthAccount> findById(Long id){throw new AssertionError();}
        public AuthAccount createOrResume(AuthAccount a,java.util.function.Consumer<AuthAccount> check){order.add("create");if(winner!=null){check.accept(winner);return winner;}created=a;return a;}
        public AuthAccount save(AuthAccount a){order.add("save");return a;}
        public String hash(String raw){return "hash";}
        public boolean matches(String raw,String hash){return matches;}
        public AuthAccount provision(AuthAccount a){order.add("profile");return a.withProvisionedProfile(11L);}
    }
}
