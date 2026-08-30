package com.delivery.auth_service.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import com.delivery.identity.contracts.SimulationContext;

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
    void refreshTokensCarryFamilyTypeAndUniqueIdentity() throws Exception {
        KeyPair keyPair = generateKeyPair();
        Path privateKey = write("family-private.pem", keyPair.getPrivate().getEncoded());
        Path publicKey = write("family-public.pem", keyPair.getPublic().getEncoded());
        TokenService tokenService = new TokenService(privateKey.toString(), publicKey.toString());

        String first = tokenService.generateRefreshToken(
                7L, "user@example.test", "USER", "family-7");
        String second = tokenService.generateRefreshToken(
                7L, "user@example.test", "USER", "family-7");
        Map<String, Object> firstClaims = claims(first);
        Map<String, Object> secondClaims = claims(second);

        assertEquals("refresh", firstClaims.get("token_type"));
        assertEquals("family-7", firstClaims.get("token_family"));
        assertNotEquals(firstClaims.get("jti"), secondClaims.get("jti"));
        assertNotEquals(first, second);
        assertTrue(tokenService.isValidRefreshToken(first));
    }

    @Test
    void accessTokenExpiryIsEnforcedBeforeRefreshFlow() throws Exception {
        KeyPair keyPair = generateKeyPair();
        Path privateKey = write("expiring-private.pem", keyPair.getPrivate().getEncoded());
        Path publicKey = write("expiring-public.pem", keyPair.getPublic().getEncoded());
        TokenService tokenService = new TokenService(
                privateKey.toString(), publicKey.toString(), "", 1L);

        String token = tokenService.generateToken(7L, "user@example.test", "USER");
        assertTrue(tokenService.isValid(token));
        Thread.sleep(1_200L);
        assertFalse(tokenService.isValid(token));
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
    void accessTokenCarriesOnlyServerSuppliedSimulationContext() throws Exception {
        KeyPair keyPair = generateKeyPair();
        Path privateKey = write("simulation-private.pem", keyPair.getPrivate().getEncoded());
        Path publicKey = write("simulation-public.pem", keyPair.getPublic().getEncoded());
        TokenService tokenService = new TokenService(privateKey.toString(), publicKey.toString());
        UUID runId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();

        String token = tokenService.generateToken(7L, 9L, "shipper@example.test", "SHIPPER",
                new SimulationContext(SimulationContext.ExecutionMode.SIMULATION, runId, cohortId, 2L));

        Map<String, Object> claims = claims(token);
        assertEquals("SIMULATION", claims.get("simulation_mode"));
        assertEquals(runId.toString(), claims.get("simulation_run_id"));
        assertEquals(cohortId.toString(), claims.get("simulation_cohort_id"));
        assertEquals(2, ((Number) claims.get("simulation_binding_version")).intValue());
    }

    @Test
    void accessTokenAndJwksUseTheConfiguredKidIssuerAndAudience() throws Exception {
        KeyPair keyPair = generateKeyPair();
        Path privateKey = write("contract-private.pem", keyPair.getPrivate().getEncoded());
        Path publicKey = write("contract-public.pem", keyPair.getPublic().getEncoded());
        TokenService tokenService = new TokenService(
                privateKey.toString(), publicKey.toString(), "", 900L,
                "auth-key-current", "", "delivery-auth-test", "delivery-api-test");

        String token = tokenService.generateToken(7L, "user@example.test", "USER");
        Map<String, Object> header = new ObjectMapper().readValue(
                Base64.getUrlDecoder().decode(token.split("\\.")[0]), new TypeReference<>() {});
        Map<String, Object> claims = claims(token);

        assertEquals("RS256", header.get("alg"));
        assertEquals("auth-key-current", header.get("kid"));
        assertEquals("delivery-auth-test", claims.get("iss"));
        assertEquals(java.util.List.of("delivery-api-test"), claims.get("aud"));
        assertEquals(java.util.List.of("USER"), claims.get("roles"));
        assertEquals("access", claims.get("token_type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> jwk = (Map<String, Object>) ((java.util.List<?>) tokenService.getJwks()
                .get("keys")).get(0);
        assertEquals("RSA", jwk.get("kty"));
        assertEquals("RS256", jwk.get("alg"));
        assertEquals("auth-key-current", jwk.get("kid"));
        assertTrue(jwk.containsKey("n"));
        assertTrue(jwk.containsKey("e"));
        assertFalse(jwk.containsKey("d"));
    }

    @Test
    void previousPublicKeyRequiresAndPublishesItsOriginalKid() throws Exception {
        KeyPair currentPair = generateKeyPair();
        KeyPair retiringPair = generateKeyPair();
        Path privateKey = write("current-private.pem", currentPair.getPrivate().getEncoded());
        Path publicKey = write("current-public.pem", currentPair.getPublic().getEncoded());
        Path retiringPublicKey = write("retiring-public.pem", retiringPair.getPublic().getEncoded());

        TokenService tokenService = new TokenService(
                privateKey.toString(), publicKey.toString(), retiringPublicKey.toString(), 900L,
                "auth-key-current", "auth-key-retiring", "delivery-auth", "delivery-api");

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> keys = (java.util.List<Map<String, Object>>) tokenService.getJwks()
                .get("keys");
        assertEquals(java.util.List.of("auth-key-current", "auth-key-retiring"),
                keys.stream().map(key -> (String) key.get("kid")).toList());

        assertThrows(IllegalStateException.class, () -> new TokenService(
                privateKey.toString(), publicKey.toString(), retiringPublicKey.toString(), 900L,
                "auth-key-current", "", "delivery-auth", "delivery-api"));
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

    private Map<String, Object> claims(String token) throws Exception {
        return new ObjectMapper().readValue(
                Base64.getUrlDecoder().decode(token.split("\\.")[1]), new TypeReference<>() {});
    }

    private Path write(String filename, byte[] encodedKey) throws Exception {
        Path destination = tempDir.resolve(filename);
        Files.writeString(destination, Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encodedKey));
        return destination;
    }
}
