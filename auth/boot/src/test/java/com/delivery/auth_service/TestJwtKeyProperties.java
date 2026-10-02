package com.delivery.auth_service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.springframework.test.context.DynamicPropertyRegistry;

public final class TestJwtKeyProperties {

    private static final Path PRIVATE_KEY_PATH;
    private static final Path PUBLIC_KEY_PATH;

    static {
        try {
            Path directory = Files.createTempDirectory("auth-service-test-jwt-keys");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            PRIVATE_KEY_PATH = write(directory.resolve("private.pem"), keyPair.getPrivate().getEncoded());
            PUBLIC_KEY_PATH = write(directory.resolve("public.pem"), keyPair.getPublic().getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create test JWT keypair", e);
        }
    }

    private TestJwtKeyProperties() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("jwt.private-key.path", PRIVATE_KEY_PATH::toString);
        registry.add("jwt.public-key.path", PUBLIC_KEY_PATH::toString);
    }

    private static Path write(Path destination, byte[] encodedKey) throws Exception {
        Files.writeString(destination,
                Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encodedKey));
        return destination;
    }
}
