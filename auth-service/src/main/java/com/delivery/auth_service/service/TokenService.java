package com.delivery.auth_service.service;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.delivery.auth_service.exception.InvalidTokenException;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.util.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class TokenService {

    static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String TOKEN_FAMILY_CLAIM = "token_family";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final List<PublicKey> previousPublicKeys;
    private final Duration accessTokenTtl;
    private final String activeKid;
    private final String retiringKid;
    private final String issuer;
    private final String audience;

    @Autowired
    public TokenService(
            @Value("${jwt.private-key.path:}") String privateKeyLocation,
            @Value("${jwt.public-key.path:}") String publicKeyLocation,
            @Value("${jwt.previous-public-key.path:}") String previousPublicKeyLocation,
            @Value("${jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds,
            @Value("${jwt.active-kid:auth-key-1}") String activeKid,
            @Value("${jwt.retiring-kid:}") String retiringKid,
            @Value("${jwt.issuer:${JWT_ISSUER:delivery-auth}}") String issuer,
            @Value("${jwt.audience:${JWT_AUDIENCE:delivery-api}}") String audience) {
        try {
            this.privateKey = loadPrivateKey(privateKeyLocation);
            this.publicKey = loadPublicKey(publicKeyLocation);
            this.previousPublicKeys = previousPublicKeyLocation == null || previousPublicKeyLocation.isBlank()
                    ? List.of()
                    : List.of(loadPublicKey(previousPublicKeyLocation));
            verifyKeyPair(this.privateKey, this.publicKey);
            if (accessTokenTtlSeconds <= 0) {
                throw new IllegalArgumentException("JWT access token TTL must be positive");
            }
            if (activeKid == null || activeKid.isBlank()) {
                throw new IllegalArgumentException("JWT active kid must not be blank");
            }
            if (issuer == null || issuer.isBlank()) {
                throw new IllegalArgumentException("JWT issuer must not be blank");
            }
            if (audience == null || audience.isBlank()) {
                throw new IllegalArgumentException("JWT audience must not be blank");
            }
            if (!previousPublicKeys.isEmpty() && (retiringKid == null || retiringKid.isBlank())) {
                throw new IllegalArgumentException("JWT retiring kid is required when a previous public key is configured");
            }
            if (!previousPublicKeys.isEmpty() && activeKid.equals(retiringKid)) {
                throw new IllegalArgumentException("JWT active kid and retiring kid must be different");
            }
            this.accessTokenTtl = Duration.ofSeconds(accessTokenTtlSeconds);
            this.activeKid = activeKid;
            this.retiringKid = retiringKid;
            this.issuer = issuer;
            this.audience = audience;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to initialize JWT RSA keys from configured locations", e);
        }
    }

    TokenService(String privateKeyLocation, String publicKeyLocation) {
        this(privateKeyLocation, publicKeyLocation, "", 900L, "auth-key-1", "", "delivery-auth", "delivery-api");
    }

    TokenService(String privateKeyLocation, String publicKeyLocation, String previousPublicKeyLocation, long accessTokenTtlSeconds) {
        this(privateKeyLocation, publicKeyLocation, previousPublicKeyLocation, accessTokenTtlSeconds,
                "auth-key-1", "", "delivery-auth", "delivery-api");
    }

    private PrivateKey loadPrivateKey(String location) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(readKeyMaterial(location));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private PublicKey loadPublicKey(String location) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(readKeyMaterial(location));
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private String readKeyMaterial(String location) throws Exception {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("JWT key location must not be blank");
        }
        String raw;
        if (location.startsWith("classpath:")) {
            String classpathName = location.substring("classpath:".length());
            while (classpathName.startsWith("/")) {
                classpathName = classpathName.substring(1);
            }
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(classpathName)) {
                if (is == null) {
                    throw new IllegalStateException("JWT key resource not found: " + location);
                }
                raw = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            String filesystemPath = location.startsWith("file:")
                    ? location.substring("file:".length())
                    : location;
            raw = Files.readString(Path.of(filesystemPath), StandardCharsets.UTF_8);
        }
        return raw.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
    }

    private void verifyKeyPair(PrivateKey signingKey, PublicKey verificationKey) throws Exception {
        byte[] probe = "delivery-jwt-key-preflight".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(signingKey);
        signer.update(probe);

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(verificationKey);
        verifier.update(probe);
        if (!verifier.verify(signer.sign())) {
            throw new InvalidKeySpecException("JWT private and public keys do not form a pair");
        }
    }

    public String generateToken(Long userId, String email, String role) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .setHeaderParam("kid", activeKid)
                .setIssuer(issuer)
                .claim("aud", List.of(audience))
                .setSubject(String.valueOf(userId))
                .claim("email", email)
                .claim("roles", List.of(role))
                .claim("role", role)
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(issuedAt.plus(accessTokenTtl)))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    public String generateRefreshToken(Long userId, String email, String role) {
        return generateRefreshToken(userId, email, role, UUID.randomUUID().toString());
    }

    public String generateRefreshToken(Long userId, String email, String role, String tokenFamilyId) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .setHeaderParam("kid", activeKid)
                .setIssuer(issuer)
                .setSubject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .claim(TOKEN_FAMILY_CLAIM, tokenFamilyId)
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(issuedAt.plus(REFRESH_TOKEN_TTL)))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    public Map<String, Object> getJwks() {
        List<Map<String, Object>> keys = new ArrayList<>();
        if (publicKey instanceof RSAPublicKey rsaKey) {
            keys.add(toJwkMap(rsaKey, activeKid));
        }
        if (previousPublicKeys != null) {
            for (PublicKey pk : previousPublicKeys) {
                if (pk instanceof RSAPublicKey rsaKey) {
                    keys.add(toJwkMap(rsaKey, retiringKid));
                }
            }
        }
        return Map.of("keys", keys);
    }

    private Map<String, Object> toJwkMap(RSAPublicKey key, String kid) {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("alg", "RS256");
        jwk.put("use", "sig");
        jwk.put("kid", kid);
        jwk.put("n", encodeBigInteger(key.getModulus()));
        jwk.put("e", encodeBigInteger(key.getPublicExponent()));
        return jwk;
    }

    private String encodeBigInteger(BigInteger val) {
        byte[] bytes = val.toByteArray();
        if (bytes[0] == 0 && bytes.length > 1) {
            byte[] tmp = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, tmp, 0, tmp.length);
            bytes = tmp;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String extractEmail(String token) {
        return parseClaims(token)
                .getBody()
                .get("email", String.class);
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public boolean isValidRefreshToken(String token) {
        try {
            String tokenType = parseClaims(token).getBody().get(TOKEN_TYPE_CLAIM, String.class);
            return tokenType == null || REFRESH_TOKEN_TYPE.equals(tokenType);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractRefreshTokenFamily(String token) {
        return parseClaims(token).getBody().get(TOKEN_FAMILY_CLAIM, String.class);
    }

    public LocalDateTime refreshTokenExpiresAt() {
        return LocalDateTime.ofInstant(Instant.now().plus(REFRESH_TOKEN_TTL), ZoneOffset.UTC);
    }

    public String extractUsername(String token) {
        try {
            return extractEmail(token);
        } catch (JwtException e) {
            throw new InvalidTokenException("Failed to extract username from token");
        }
    }

    public boolean isTokenValid(String token, org.springframework.security.core.userdetails.UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        try {
            Date expiration = parseClaims(token)
                    .getBody()
                    .getExpiration();
            return expiration.before(new Date());
        } catch (JwtException e) {
            throw new InvalidTokenException("Token expired or invalid");
        }
    }

    private Jws<Claims> parseClaims(String token) {
        JwtException lastFailure = null;
        for (PublicKey verificationKey : verificationKeys()) {
            try {
                return Jwts.parserBuilder()
                        .setSigningKey(verificationKey)
                        .build()
                        .parseClaimsJws(token);
            } catch (JwtException failure) {
                lastFailure = failure;
            }
        }
        throw lastFailure == null ? new JwtException("No JWT verification key is configured") : lastFailure;
    }

    private List<PublicKey> verificationKeys() {
        java.util.ArrayList<PublicKey> keys = new java.util.ArrayList<>();
        keys.add(publicKey);
        keys.addAll(previousPublicKeys);
        return keys;
    }
}
