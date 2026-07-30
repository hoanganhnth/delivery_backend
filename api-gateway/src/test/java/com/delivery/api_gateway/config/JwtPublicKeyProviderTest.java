package com.delivery.api_gateway.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void retainsOneRetiringPublicKeyForAControlledRotationWindow() throws Exception {
        KeyPair current = generateKeyPair();
        KeyPair previous = generateKeyPair();
        Path currentPath = tempDir.resolve("current.pem");
        Path previousPath = tempDir.resolve("previous.pem");
        Files.writeString(currentPath, Base64.getEncoder().encodeToString(current.getPublic().getEncoded()));
        Files.writeString(previousPath, Base64.getEncoder().encodeToString(previous.getPublic().getEncoded()));

        JwtPublicKeyProvider provider = new JwtPublicKeyProvider(currentPath.toString(), previousPath.toString());

        assertThat(provider.getPreviousPublicKeys()).singleElement()
                .extracting(key -> key.getEncoded())
                .isEqualTo(previous.getPublic().getEncoded());
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
