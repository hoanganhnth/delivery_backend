package com.delivery.web_bff.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import com.delivery.web_bff.application.api.Ports.HttpVerb;

class ApiProxyPolicyTest {
    @Test
    void allowsPlatformResourcesButNeverCredentialOrInternalEndpoints() {
        assertThat(new ApiProxyPolicyApplicationService().execute(HttpVerb.GET, "/api/users")).isTrue();
        assertThat(new ApiProxyPolicyApplicationService().execute(HttpVerb.POST, "/api/orders/checkout-preview")).isTrue();
        assertThat(new ApiProxyPolicyApplicationService().execute(HttpVerb.POST, "/api/auth/firebase/chat-token")).isTrue();
        assertThat(new ApiProxyPolicyApplicationService().execute(HttpVerb.POST, "/api/auth/login")).isFalse();
        assertThat(new ApiProxyPolicyApplicationService().execute(HttpVerb.POST, "/api/auth/refresh-token")).isFalse();
        assertThat(new ApiProxyPolicyApplicationService().execute(HttpVerb.GET, "/api/internal/users/42")).isFalse();
        assertThat(new ApiProxyPolicyApplicationService().execute(HttpVerb.GET, "/api/users/42/internal/audit")).isFalse();
        assertThat(new ApiProxyPolicyApplicationService().execute(HttpVerb.GET, "/api/restaurants/internal/metrics")).isFalse();
        assertThat(new ApiProxyPolicyApplicationService().execute(HttpVerb.GET, "/api/users/../internal")).isFalse();
        assertThat(new ApiProxyPolicyApplicationService().execute(null, "/api/users")).isFalse();
    }
}
