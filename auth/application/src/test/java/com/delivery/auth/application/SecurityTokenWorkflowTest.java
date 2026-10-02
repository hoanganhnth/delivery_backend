package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.model.SecurityTokenPurpose;
import com.delivery.auth.domain.policy.InvalidAuthToken;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SecurityTokenWorkflowTest {
    private static final String IP="127.0.0.1";
    @Test void uniformRequestAndEligibleIssuanceDoNotExposeAccountExistence() {
        Fake f=new Fake();f.account=null;
        f.core().requestPasswordReset(" MISSING@EXAMPLE.TEST ",IP);
        assertThat(f.events).containsExactly("lookup:missing@example.test","audit:PASSWORD_RESET_REQUEST:ACCEPTED");
        f=new Fake();f.account=account(null,false,false);
        f.core().requestPasswordReset("user@example.test",IP);
        assertThat(f.events).contains("audit:PASSWORD_RESET_REQUEST:ACCEPTED").doesNotContain("issue");
        f=new Fake();f.core().requestPasswordReset(" USER@example.test ",IP);
        assertThat(f.events).containsSubsequence("lookup:user@example.test","consumeOutstanding:PASSWORD_RESET","random","issue","email:PASSWORD_RESET","audit:PASSWORD_RESET_REQUEST:QUEUED");
        assertThat(f.issuedExpires).isAfter(LocalDateTime.now().plusMinutes(14));
        assertThat(f.issuedExpires).isBefore(LocalDateTime.now().plusMinutes(16));
        assertThat(f.sentRaw).isEqualTo("opaque-raw-token");
        f=new Fake();f.core().requestEmailVerification(null,IP);
        assertThat(f.events).contains("lookup:","audit:EMAIL_VERIFICATION_REQUEST:ACCEPTED");
        f=new Fake();f.account=account(7L,true,false);
        f.core().requestEmailVerification("user@example.test",IP);
        assertThat(f.events).contains("email:EMAIL_VERIFICATION");
        assertThat(f.issuedExpires).isAfter(LocalDateTime.now().plusHours(23));
        f=new Fake();f.account=account(7L,true,true);
        f.core().requestEmailVerification("user@example.test",IP);
        assertThat(f.events).doesNotContain("issue").contains("audit:EMAIL_VERIFICATION_REQUEST:ACCEPTED");
    }
    @Test void resetRequiresOneTimeTokenThenChangesOnlyCredentialAndRevokesEverySession() {
        Fake f=new Fake();f.token=token(SecurityTokenPurpose.PASSWORD_RESET,null,true,LocalDateTime.now().plusMinutes(10));
        f.core().resetPassword("opaque-raw-token","NewPassword1!",IP);
        assertThat(f.account.passwordHash()).isEqualTo("hashed:NewPassword1!");
        assertThat(f.account.simulationActor()).isTrue();
        assertThat(f.events).containsSubsequence("lockToken","hash","saveAccount","consume:4","consumeOutstanding:PASSWORD_RESET","revokeAll","audit:PASSWORD_RESET_COMPLETE:SUCCESS");
        assertThat(f.events).doesNotContain("email:PASSWORD_RESET");
    }
    @Test void invalidExpiredWrongPurposeReusedAndInactiveTokensAllFailUniformly() {
        for(var value:List.of("invalid","wrong","used","expired","inactive")) {
            Fake f=new Fake();
            f.token=switch(value) {
                case "invalid" -> null;
                case "wrong" -> token(SecurityTokenPurpose.EMAIL_VERIFICATION,null,true,LocalDateTime.now().plusMinutes(10));
                case "used" -> token(SecurityTokenPurpose.PASSWORD_RESET,LocalDateTime.now(),true,LocalDateTime.now().plusMinutes(10));
                case "expired" -> token(SecurityTokenPurpose.PASSWORD_RESET,null,true,LocalDateTime.now().minusSeconds(1));
                default -> token(SecurityTokenPurpose.PASSWORD_RESET,null,false,LocalDateTime.now().plusMinutes(10));
            };
            assertThatThrownBy(()->f.core().resetPassword("token","password",IP))
                    .isInstanceOf(InvalidAuthToken.class).hasMessage("Invalid or expired security token");
            assertThat(f.events).anyMatch(s->s.startsWith("reject:PASSWORD_RESET_CONSUME:"));
            assertThat(f.events).doesNotContain("saveAccount","revokeAll");
        }
        Fake f=new Fake();var blank=f;
        assertThatThrownBy(()->blank.core().verifyEmail(" ",IP)).isInstanceOf(InvalidAuthToken.class);
        assertThat(f.events).contains("reject:EMAIL_VERIFICATION_CONSUME:INVALID");
        f=new Fake();f.token=token(SecurityTokenPurpose.PASSWORD_RESET,null,true,null);var nullExpiry=f;
        assertThatThrownBy(()->nullExpiry.core().resetPassword("token","password",IP))
                .isInstanceOf(InvalidAuthToken.class);
    }
    @Test void verificationActivatesOnlyEligibleLinkedAccountAndRecordsOutbox() {
        Fake f=new Fake();f.account=account(7L,true,false);
        f.token=token(SecurityTokenPurpose.EMAIL_VERIFICATION,null,true,LocalDateTime.now().plusHours(1));
        f.core().verifyEmail("token",IP);
        assertThat(f.account.emailVerifiedAt()).isNotNull();
        assertThat(f.account.emailVerificationRequired()).isFalse();
        assertThat(f.account.lifecycleStatus()).isEqualTo(AuthAccount.LifecycleStatus.ACTIVE);
        assertThat(f.account.lifecycleVersion()).isEqualTo(1L);
        assertThat(f.events).containsSubsequence("saveAccount","consume:4","consumeOutstanding:EMAIL_VERIFICATION","outbox:EMAIL_VERIFIED","audit:EMAIL_VERIFICATION_COMPLETE:SUCCESS");
        f=new Fake();f.account=account(null,true,false);
        f.token=token(SecurityTokenPurpose.EMAIL_VERIFICATION,null,true,LocalDateTime.now().plusHours(1));
        f.core().verifyEmail("token",IP);assertThat(f.account.lifecycleStatus()).isEqualTo(AuthAccount.LifecycleStatus.PENDING_PROFILE);
        assertThat(f.events).doesNotContain("outbox:EMAIL_VERIFIED");
        f=new Fake();f.account=account(7L,true,false).withLifecycleStatus(AuthAccount.LifecycleStatus.ACTIVE);
        f.token=token(SecurityTokenPurpose.EMAIL_VERIFICATION,null,true,LocalDateTime.now().plusHours(1));
        f.core().verifyEmail("token",IP);assertThat(f.events).doesNotContain("outbox:EMAIL_VERIFIED");
    }
    @Test void cleanupUsesClampedRetentionAndRejectsInvalidTtls() {
        Fake f=new Fake();f.core(Duration.ofMinutes(1),Duration.ofHours(1),0,0).cleanup();
        assertThat(f.events).containsExactly("deleteTokens","deleteAudits");
        assertThat(f.tokenCutoff).isBefore(LocalDateTime.now().minusHours(23));
        assertThat(f.auditCutoff).isBefore(LocalDateTime.now().minusDays(29));
        assertThatThrownBy(()->f.core(Duration.ZERO,Duration.ofHours(1),30,180))
                .hasMessage("password reset TTL must be positive");
        assertThatThrownBy(()->f.core(Duration.ofMinutes(1),Duration.ofSeconds(-1),30,180))
                .hasMessage("email verification TTL must be positive");
    }
    private static SecurityTokenPort.Token token(SecurityTokenPurpose purpose,LocalDateTime consumed,
            Boolean active,LocalDateTime expires) {
        return new SecurityTokenPort.Token(4L,7L,purpose,expires,consumed,active);
    }
    private static AuthAccount account(Long user,Boolean active,Boolean verified) {
        return new AuthAccount(7L,user,AuthAccount.LifecycleStatus.PENDING_PROFILE,0L,"user@example.test","old-hash",
                AuthAccount.Role.USER,active,true,verified?LocalDateTime.now():null,false,0L,null,null,0,null,
                true,UUID.randomUUID(),null,0L,null,null,null);
    }
    private static class Fake implements AuthTransactionPort,AuthAccountPort,SecurityTokenPort,PasswordCredentialPort,
            SecurityAuditPort,SecurityEmailPort,AccountLifecyclePort {
        AuthAccount account=account(7L,true,false);Token token;LocalDateTime issuedExpires,tokenCutoff,auditCutoff;
        String sentRaw;List<String> events=new ArrayList<>();
        DefaultSecurityTokenUseCase core(){return core(Duration.ofMinutes(15),Duration.ofHours(24),30,180);}
        DefaultSecurityTokenUseCase core(Duration reset,Duration verification,int tokenDays,int auditDays){
            return new DefaultSecurityTokenUseCase(this,this,this,this,this,this,this,reset,verification,tokenDays,auditDays);
        }
        public <T>T required(Supplier<T> operation){return operation.get();}
        public Optional<AuthAccount> findByEmail(String email){events.add("lookup:"+email);return account != null && email.equals(account.email()) ? Optional.of(account) : Optional.empty();}
        public Optional<AuthAccount> findById(Long id){return Optional.ofNullable(account);}
        public AuthAccount save(AuthAccount a){account=a;events.add("saveAccount");return a;}
        public AuthAccount createOrResume(AuthAccount a,Consumer<AuthAccount> verify){throw new UnsupportedOperationException();}
        public String hash(String raw){events.add("hash");return "hashed:"+raw;}
        public boolean matches(String raw,String hash){throw new UnsupportedOperationException();}
        public String randomRawToken(){events.add("random");return "opaque-raw-token";}
        public Optional<Token> findForUpdate(String raw){events.add("lockToken");return Optional.ofNullable(token);}
        public void consumeOutstanding(Long id,SecurityTokenPurpose purpose,LocalDateTime at){events.add("consumeOutstanding:"+purpose);}
        public void issue(Long id,SecurityTokenPurpose purpose,String raw,LocalDateTime expires){events.add("issue");issuedExpires=expires;}
        public void consume(Long id,LocalDateTime at){events.add("consume:"+id);}
        public void revokeCredentials(Long id,LocalDateTime at){events.add("revokeAll");}
        public void deleteExpiredBefore(LocalDateTime cutoff){events.add("deleteTokens");tokenCutoff=cutoff;}
        public void recordTransactional(Long id,String action,String outcome,String subject,String ip){events.add("audit:"+action+":"+outcome);}
        public void recordRejection(String action,String outcome,String ip){events.add("reject:"+action+":"+outcome);}
        public void deleteOlderThan(LocalDateTime cutoff){events.add("deleteAudits");auditCutoff=cutoff;}
        public void publish(Long id,String recipient,SecurityTokenPurpose purpose,String raw,String ip){events.add("email:"+purpose);sentRaw=raw;}
        public Change updateLocked(Long id,UnaryOperator<AuthAccount> change){throw new UnsupportedOperationException();}
        public List<AuthAccount> pending(int limit){throw new UnsupportedOperationException();}
        public int clearPending(Long id,Long version,LocalDateTime at){throw new UnsupportedOperationException();}
        public void recordFailure(Long id,Long version,String message,LocalDateTime at){throw new UnsupportedOperationException();}
        public void statusChanged(AuthAccount account,Long adminId,String reason){events.add("outbox:"+reason);}
    }
}
