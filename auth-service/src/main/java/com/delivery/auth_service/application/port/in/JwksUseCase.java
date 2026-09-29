package com.delivery.auth_service.application.port.in;

import java.util.Map;

/** Inbound application port for the public JWKS representation. */
public interface JwksUseCase {
    Map<String, Object> getJwks();
}
