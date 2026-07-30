package com.delivery.auth_service.service;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.delivery.auth_service.exception.InvalidTokenException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.*;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.time.Duration;
import java.time.Instant;

@Service
public class TokenService {

    static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final List<PublicKey> previousPublicKeys;
    private final Duration accessTokenTtl;

    @Autowired
    public TokenService(
            @Value("${jwt.private-key.path:}") String privateKeyLocation,
            @Value("${jwt.public-key.path:}") String publicKeyLocation,
            @Value("${jwt.previous-public-key.path:}") String previousPublicKeyLocation,
            @Value("${jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds) {
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
            this.accessTokenTtl = Duration.ofSeconds(accessTokenTtlSeconds);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to initialize JWT RSA keys from configured locations", e);
        }
    }

    TokenService(String privateKeyLocation, String publicKeyLocation) {
        this(privateKeyLocation, publicKeyLocation, "", 900L);
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

    /**
     * Đọc nội dung PEM từ Spring-configured location. Hỗ trợ classpath resource,
     * file URI và filesystem path tuyệt đối để secret manager có thể mount file.
     */
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
                .setSubject(String.valueOf(userId)) // có thể dùng userId làm subject
                .claim("email", email)
                .claim("role", role)
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(issuedAt.plus(accessTokenTtl)))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    public String generateRefreshToken(Long userId, String email, String role) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .setSubject(String.valueOf(userId)) // có thể dùng userId làm subject
                .claim("email", email)
                .claim("role", role)
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(issuedAt.plus(REFRESH_TOKEN_TTL)))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
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
