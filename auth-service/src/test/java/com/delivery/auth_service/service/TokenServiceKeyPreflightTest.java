package com.delivery.auth_service.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenServiceKeyPreflightTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsConfiguredFilesystemKeyPairAndSignsVerifiableToken() throws Exception {
        KeyPair keyPair = generateKeyPair();
        Path privateKey = write("private.pem", keyPair.getPrivate().getEncoded());
        Path publicKey = write("public.pem", keyPair.getPublic().getEncoded());

        TokenService tokenService = new TokenService(privateKey.toString(), publicKey.toString());
        String token = tokenService.generateToken(7L, "user@example.test", "USER");

        assertTrue(tokenService.isValid(token));
    }

    @Test
    void refreshTokenClaimMatchesSevenDaySessionAuthority() throws Exception {
        KeyPair keyPair = generateKeyPair();
        Path privateKey = write("refresh-private.pem", keyPair.getPrivate().getEncoded());
        Path publicKey = write("refresh-public.pem", keyPair.getPublic().getEncoded());
        TokenService tokenService = new TokenService(privateKey.toString(), publicKey.toString());

        String token = tokenService.generateRefreshToken(7L, "user@example.test", "USER");
        String payload = token.split("\\.")[1];
        Map<String, Object> claims = new ObjectMapper().readValue(
                Base64.getUrlDecoder().decode(payload), new TypeReference<>() {});

        long issuedAt = ((Number) claims.get("iat")).longValue();
        long expiresAt = ((Number) claims.get("exp")).longValue();
        assertTrue(expiresAt - issuedAt == java.time.Duration.ofDays(7).toSeconds());
    }

    @Test
    void accessTokenUsesConfirmedFifteenMinuteDefault() throws Exception {
        KeyPair keyPair = generateKeyPair();
        Path privateKey = write("access-private.pem", keyPair.getPrivate().getEncoded());
        Path publicKey = write("access-public.pem", keyPair.getPublic().getEncoded());
        TokenService tokenService = new TokenService(privateKey.toString(), publicKey.toString());

        String token = tokenService.generateToken(7L, "user@example.test", "USER");
        String payload = token.split("\\.")[1];
        Map<String, Object> claims = new ObjectMapper().readValue(
                Base64.getUrlDecoder().decode(payload), new TypeReference<>() {});

        long issuedAt = ((Number) claims.get("iat")).longValue();
        long expiresAt = ((Number) claims.get("exp")).longValue();
        assertTrue(expiresAt - issuedAt == java.time.Duration.ofMinutes(15).toSeconds());
    }

    @Test
    void rejectsMismatchedKeyPairDuringStartup() throws Exception {
        KeyPair signingPair = generateKeyPair();
        KeyPair unrelatedPair = generateKeyPair();
        Path privateKey = write("private.pem", signingPair.getPrivate().getEncoded());
        Path publicKey = write("public.pem", unrelatedPair.getPublic().getEncoded());

        assertThrows(IllegalStateException.class,
                () -> new TokenService(privateKey.toString(), publicKey.toString()));
    }

    @Test
    void rejectsMissingConfiguredKeyDuringStartup() {
        Path missingPrivateKey = tempDir.resolve("missing-private.pem");
        Path missingPublicKey = tempDir.resolve("missing-public.pem");

        assertThrows(IllegalStateException.class,
                () -> new TokenService(missingPrivateKey.toString(), missingPublicKey.toString()));
    }

    @Test
    void rejectsBlankConfiguredKeyDuringStartup() {
        assertThrows(IllegalStateException.class,
                () -> new TokenService("", ""));
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private Path write(String filename, byte[] encodedKey) throws Exception {
        Path destination = tempDir.resolve(filename);
        Files.writeString(destination, Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encodedKey));
        return destination;
    }
}
