package com.delivery.api_gateway.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JwtPublicKeyProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsConfiguredFilesystemPublicKey() throws Exception {
        KeyPair keyPair = generateKeyPair();
        Path publicKey = tempDir.resolve("public.pem");
        Files.writeString(publicKey,
                Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(keyPair.getPublic().getEncoded()));

        JwtPublicKeyProvider provider = new JwtPublicKeyProvider(publicKey.toString());

        assertArrayEquals(keyPair.getPublic().getEncoded(), provider.getPublicKey().getEncoded());
    }

    @Test
    void rejectsMissingConfiguredPublicKey() {
        assertThrows(IllegalStateException.class,
                () -> new JwtPublicKeyProvider(tempDir.resolve("missing.pem").toString()));
    }

    @Test
    void rejectsBlankConfiguredPublicKey() {
        assertThrows(IllegalStateException.class,
                () -> new JwtPublicKeyProvider(""));
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
