package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RegistrationAdmissionWorkflowTest {
    @Test void masterSwitchWinsAndAllowlistPrecedesThePercentageBucket() {
        Fake f=new Fake();
        assertThat(f.useCase(false,100,"CANARY@example.com",false).admits("canary@example.com")).isFalse();
        assertThat(f.events).containsExactly("false:master_disabled");assertThat(f.lookups).isZero();
        f.events.clear();
        assertThat(f.useCase(true,0," , CANARY@example.com, , canary@example.com ",false).admits(" CANARY@example.com ")).isTrue();
        assertThat(f.events).containsExactly("true:allowlist");assertThat(f.lookups).isZero();
    }
    @Test void fullOrClosedRolloutsAvoidHashingAndPartialRolloutUsesCanonicalStrictThreshold() {
        Fake f=new Fake();
        assertThat(f.useCase(true,100,null,false).admits(null)).isTrue();
        assertThat(f.useCase(true,0," ",false).admits("a@example.com")).isFalse();
        assertThat(f.lookups).isZero();
        f.bucket=49;assertThat(f.useCase(true,50,"",true).admits(" A@example.com ")).isTrue();
        assertThat(f.email).isEqualTo("a@example.com");
        f.bucket=50;assertThat(f.useCase(true,50,"",true).admits(null)).isFalse();assertThat(f.email).isEmpty();
        assertThat(f.events).containsExactly("true:percentage","false:cohort_closed","true:percentage","false:cohort_closed");
    }
    @Test void startupRejectsInvalidPercentageAndMissingSecretEvenWhenMasterIsClosed() {
        Fake f=new Fake();
        for (int percentage:new int[]{-1,101}) {
            assertThatThrownBy(() -> f.useCase(false,percentage,"",true))
                    .hasMessage("app.identity.registration.canary-percentage must be between 0 and 100");
        }
        for (int percentage:new int[]{1,99}) {
            assertThatThrownBy(() -> f.useCase(false,percentage,"",false))
                    .hasMessage("A registration canary hash key is required when percentage is between 1 and 99");
        }
        assertThat(f.events).isEmpty();
    }
    static final class Fake implements RegistrationCohortPort, RegistrationAdmissionTelemetryPort {
        int bucket,lookups;String email;List<String> events=new ArrayList<>();
        DefaultRegistrationAdmissionUseCase useCase(boolean enabled,int percentage,String allowlist,boolean key) {
            return new DefaultRegistrationAdmissionUseCase(enabled,percentage,allowlist,key,this,this);
        }
        public int bucket(String email) {lookups++;this.email=email;return bucket;}
        public void record(boolean admitted,String mechanism) {events.add(admitted+":"+mechanism);}
    }
}
