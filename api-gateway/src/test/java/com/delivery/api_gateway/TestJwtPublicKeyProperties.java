package com.delivery.api_gateway;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.springframework.test.context.DynamicPropertyRegistry;

public final class TestJwtPublicKeyProperties {

    private static final Path PUBLIC_KEY_PATH;

    static {
        try {
            Path directory = Files.createTempDirectory("api-gateway-test-jwt-keys");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            PUBLIC_KEY_PATH = write(directory.resolve("public.pem"), keyPair.getPublic().getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create test JWT public key", e);
        }
    }

    private TestJwtPublicKeyProperties() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key.path", PUBLIC_KEY_PATH::toString);
    }

    private static Path write(Path destination, byte[] encodedKey) throws Exception {
        Files.writeString(destination,
                Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encodedKey));
        return destination;
    }
}
