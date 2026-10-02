package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SocialLoginWorkflowTest {
    @Test void newIdentityUsesThePublicRoleProfileAndAtomicSessionFlow() {
        for(String role:new String[]{null," ","SHOP_OWNER"}) {
            Fake f=new Fake();var result=f.core().login(command(role,null));
            assertThat(result.role()).isEqualTo("SHOP_OWNER".equals(role)?"SHOP_OWNER":"USER");
            assertThat(f.created.email()).isEqualTo("social@example.com");
            assertThat(f.created.emailVerificationRequired()).isFalse();assertThat(f.created.emailVerifiedAt()).isNotNull();
            assertThat(f.savedSession.deviceId()).isEqualTo("social-device");
            assertThat(f.savedSession.expiresAt()).isEqualTo(f.savedSession.lastLoginAt().plusDays(7));
            assertThat(f.events).containsSubsequence("create","profile","account","revoke","deactivate","access","refresh","session","remember","commit");
        }
    }
    @Test void existingOperatorRoleIsAuthoritativeAndSocialVerificationDoesNotRewriteOnboarding() {
        Fake f=new Fake();f.account=account(AuthAccount.Role.SHIPPER,11L,true,true,LocalDateTime.now());
        var result=f.core().login(command("ADMIN"," device "));
        assertThat(result.role()).isEqualTo("SHIPPER");assertThat(f.account.emailVerificationRequired()).isFalse();
        assertThat(f.savedSession.deviceId()).isEqualTo(" device ");assertThat(f.revokedDevice).isEqualTo("device");
        assertThat(f.events).doesNotContain("hash","profile");
        f=new Fake();f.account=account(AuthAccount.Role.USER,11L,null,null,LocalDateTime.now());
        f.core().login(command("USER"," "));assertThat(f.savedSession.deviceId()).isEqualTo("social-device");
        assertThat(f.events).doesNotContain("account");
    }
    @Test void databaseWinnerKeepsItsRoleAndMissingVerificationIsSavedBeforeSessionCreation() {
        Fake f=new Fake();f.winner=account(AuthAccount.Role.ADMIN,11L,true,false,null);
        assertThat(f.core().login(command("USER","phone")).role()).isEqualTo("ADMIN");
        assertThat(f.events).containsSubsequence("create","account","access").doesNotContain("profile");
        assertThat(f.account.emailVerifiedAt()).isNotNull();
    }
    @Test void rejectsUnsupportedOrUnverifiedProviderBeforeIdentityMutation() {
        Fake f=new Fake();
        for(String provider:new String[]{null,"facebook"}) {
            assertThatThrownBy(() -> f.core().login(new SocialLoginCommand(provider,"token",null,null,null,null,null)))
                    .hasMessage("Unsupported provider: "+provider);
        }
        f.identity=null;
        assertThatThrownBy(() -> f.core().login(command(null,null))).hasMessage("Invalid Google ID token");
        for(var identity:new SocialIdentityPort.VerifiedSocialIdentity[]{
                new SocialIdentityPort.VerifiedSocialIdentity("google","a@example.com",false),
                new SocialIdentityPort.VerifiedSocialIdentity("google",null,true),
                new SocialIdentityPort.VerifiedSocialIdentity("google"," ",true)}) {
            f.identity=identity;
            assertThatThrownBy(() -> f.core().login(command(null,null))).hasMessage("Google account email is not verified");
        }
        assertThat(f.events).doesNotContain("create","access");
        assertThatThrownBy(() -> f.core().login(null)).isInstanceOf(NullPointerException.class);
    }
    @Test void rejectsNewOperatorRolesAndBlockedOrUnlinkedAccountsBeforeTokenIssuance() {
        Fake f=new Fake();
        for(String role:new String[]{"SHIPPER","ADMIN"," USER ","unknown"}) {
            assertThatThrownBy(() -> f.core().login(command(role,null))).isInstanceOf(IllegalArgumentException.class);
        }
        f.account=account(AuthAccount.Role.USER,11L,false,false,LocalDateTime.now());
        assertThatThrownBy(() -> f.core().login(command(null,null))).hasMessage("Account is blocked or inactive");
        f.account=account(AuthAccount.Role.USER,null,true,false,LocalDateTime.now());f.linkProfile=false;
        assertThatThrownBy(() -> f.core().login(command(null,null))).hasMessage("Account profile is not provisioned");
        assertThat(f.events).doesNotContain("access","session");
    }
    @Test void credentialFailureIsReportedThroughTheTransactionRollback() {
        Fake f=new Fake();f.failRemember=true;
        assertThatThrownBy(() -> f.core().login(command(null,null))).hasMessage("storage failed");
        assertThat(f.events).contains("rollback").doesNotContain("commit");
    }
    static SocialLoginCommand command(String role,String device){return new SocialLoginCommand("google","token",role,device,"Phone",Session.DeviceType.MOBILE,"ip");}
    static AuthAccount account(AuthAccount.Role role,Long user,Boolean active,Boolean required,LocalDateTime verified) {
        return new AuthAccount(7L,user,AuthAccount.LifecycleStatus.PENDING_PROFILE,2L,"social@example.com","hash",role,
                active,required,verified,false,0L,null,null,0,null,false,null,null,0L,null,null,null);
    }
    static final class Fake implements AuthTransactionPort,SocialIdentityPort,AuthAccountPort,PasswordCredentialPort,
            UserProfileProvisioningUseCase,SessionPort,SessionTokenPort,RefreshCredentialPort {
        boolean open,linkProfile=true,failRemember;AuthAccount account,winner,created;Session savedSession;String revokedDevice;
        VerifiedSocialIdentity identity=new VerifiedSocialIdentity("google"," SOCIAL@example.com ",true);
        List<String> events=new ArrayList<>();
        DefaultSocialLoginUseCase core(){return new DefaultSocialLoginUseCase(this,this,this,this,this,this,this,this);}
        public <T>T required(Supplier<T> op){open=true;try{T r=op.get();events.add("commit");return r;}catch(RuntimeException e){events.add("rollback");throw e;}finally{open=false;}}
        public Optional<VerifiedSocialIdentity> verify(String provider,String token){assertThat(open).isTrue();return Optional.ofNullable(identity);}
        public Optional<AuthAccount> findByEmail(String email){assertThat(email).isEqualTo("social@example.com");return Optional.ofNullable(account);}
        public Optional<AuthAccount> findById(Long id){throw new AssertionError();}
        public AuthAccount createOrResume(AuthAccount a,Consumer<AuthAccount> check){events.add("create");created=a;if(winner!=null){check.accept(winner);return winner;}return account(a.role(),null,true,false,a.emailVerifiedAt());}
        public AuthAccount save(AuthAccount a){events.add("account");account=a;return a;}
        public String hash(String raw){events.add("hash");return "hash";}
        public boolean matches(String raw,String hash){throw new AssertionError();}
        public AuthAccount provision(AuthAccount a){events.add("profile");return linkProfile?a.withProvisionedProfile(11L):a;}
        public Session save(Session s){events.add("session");savedSession=s;return s;}
        public List<Session> findByAccountAndDeviceForUpdate(Long id,String device){throw new AssertionError();}
        public List<Session> findActiveByAccount(Long id,LocalDateTime now,int limit){throw new AssertionError();}
        public int deactivateAllForAccount(Long id,LocalDateTime at){throw new AssertionError();}
        public int deactivateForAccountAndDevice(Long id,String device,LocalDateTime at){events.add("deactivate");return 0;}
        public String issueAccessToken(AuthAccount a){events.add("access");return "access";}
        public String issueRefreshToken(AuthAccount a,String family){events.add("refresh");return "refresh";}
        public void revokeDevice(Long id,String device,LocalDateTime at){events.add("revoke");revokedDevice=device;}
        public void rememberCurrent(Session s,String raw,LocalDateTime issued,LocalDateTime expiry){assertThat(open).isTrue();events.add("remember");if(failRemember)throw new IllegalStateException("storage failed");}
    }
}
