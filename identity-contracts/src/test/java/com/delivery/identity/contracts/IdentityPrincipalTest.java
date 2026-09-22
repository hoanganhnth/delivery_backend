package com.delivery.identity.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class IdentityPrincipalTest {

    @Test
    void preservesTheTypedPrincipalLookupWireShape() throws Exception {
        IdentityPrincipal principal = new IdentityPrincipal(
                42L, IdentityRole.SHOP_OWNER, IdentityLifecycleStatus.ACTIVE);
        ObjectMapper objectMapper = new ObjectMapper();

        String json = objectMapper.writeValueAsString(principal);

        assertEquals(
                "{\"principalId\":42,\"role\":\"SHOP_OWNER\",\"lifecycleStatus\":\"ACTIVE\"}",
                json);
        assertEquals(principal, objectMapper.readValue(json, IdentityPrincipal.class));
    }
}
