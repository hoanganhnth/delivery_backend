package com.delivery.auth_service.service;

import com.delivery.auth.application.api.RegistrationCohortPort;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HmacRegistrationCohortAdapter implements RegistrationCohortPort {
    private final byte[] cohortKey;
    public HmacRegistrationCohortAdapter(@Value("${app.identity.registration.canary-hash-key:}") String key) {
        cohortKey = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
    }
    @Override public int bucket(String canonicalEmail) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cohortKey, "HmacSHA256"));
            byte[] digest = mac.doFinal(canonicalEmail.getBytes(StandardCharsets.UTF_8));
            long value = ((long) (digest[0] & 0xff) << 24) | ((long) (digest[1] & 0xff) << 16)
                    | ((long) (digest[2] & 0xff) << 8) | (digest[3] & 0xffL);
            return (int) (value % 100);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot calculate registration cohort", exception);
        }
    }
}
