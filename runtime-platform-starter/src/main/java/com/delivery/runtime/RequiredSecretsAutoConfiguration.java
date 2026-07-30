package com.delivery.runtime;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Makes an intentionally required internal credential a startup contract rather
 * than a late request-time failure. The requirement is switched on only by a
 * deployment topology, so isolated unit tests and disabled capabilities remain
 * hermetic.
 */
@AutoConfiguration
@EnableConfigurationProperties(RequiredSecretsAutoConfiguration.RequiredSecretsProperties.class)
public class RequiredSecretsAutoConfiguration {

    @ConfigurationProperties("platform.secrets")
    public static class RequiredSecretsProperties {
        private boolean internalSecretRequired;

        public boolean isInternalSecretRequired() {
            return internalSecretRequired;
        }

        public void setInternalSecretRequired(boolean internalSecretRequired) {
            this.internalSecretRequired = internalSecretRequired;
        }
    }

    @org.springframework.context.annotation.Bean
    SmartInitializingSingleton verifyRequiredInternalSecret(
            RequiredSecretsProperties properties,
            @Value("${app.internal.secret:}") String internalSecret) {
        return () -> {
            if (properties.isInternalSecretRequired()
                    && (internalSecret == null || internalSecret.isBlank())) {
                throw new IllegalStateException(
                        "INTERNAL_SECRET is required by this deployment topology and must be injected externally");
            }
        };
    }
}
