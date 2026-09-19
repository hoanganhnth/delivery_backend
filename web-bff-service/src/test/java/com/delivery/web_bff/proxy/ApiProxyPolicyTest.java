package com.delivery.web_bff.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class ApiProxyPolicyTest {
    @Test
    void allowsPlatformResourcesButNeverCredentialOrInternalEndpoints() {
        assertThat(ApiProxyPolicy.allows(HttpMethod.GET, "/api/users")).isTrue();
        assertThat(ApiProxyPolicy.allows(HttpMethod.POST, "/api/orders/checkout-preview")).isTrue();
        assertThat(ApiProxyPolicy.allows(HttpMethod.POST, "/api/auth/firebase/chat-token")).isTrue();
        assertThat(ApiProxyPolicy.allows(HttpMethod.POST, "/api/auth/login")).isFalse();
        assertThat(ApiProxyPolicy.allows(HttpMethod.POST, "/api/auth/refresh-token")).isFalse();
        assertThat(ApiProxyPolicy.allows(HttpMethod.GET, "/api/internal/users/42")).isFalse();
        assertThat(ApiProxyPolicy.allows(HttpMethod.GET, "/api/users/42/internal/audit")).isFalse();
        assertThat(ApiProxyPolicy.allows(HttpMethod.GET, "/api/restaurants/internal/metrics")).isFalse();
        assertThat(ApiProxyPolicy.allows(HttpMethod.GET, "/api/users/../internal")).isFalse();
        assertThat(ApiProxyPolicy.allows(HttpMethod.TRACE, "/api/users")).isFalse();
    }
}
