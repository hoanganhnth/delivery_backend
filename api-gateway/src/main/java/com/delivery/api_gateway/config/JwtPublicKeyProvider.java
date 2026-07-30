package com.delivery.api_gateway.config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class JwtPublicKeyProvider {

    private final PublicKey publicKey;
    private final List<PublicKey> previousPublicKeys;

    @Autowired
    public JwtPublicKeyProvider(
            @Value("${jwt.public-key.path:}") String publicKeyLocation,
            @Value("${jwt.previous-public-key.path:}") String previousPublicKeyLocation) {
        try {
            this.publicKey = loadPublicKey(publicKeyLocation);
            this.previousPublicKeys = previousPublicKeyLocation == null || previousPublicKeyLocation.isBlank()
                    ? List.of()
                    : List.of(loadPublicKey(previousPublicKeyLocation));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to initialize JWT public key from configured location", e);
        }
    }

    JwtPublicKeyProvider(String publicKeyLocation) {
        this(publicKeyLocation, "");
    }

    private PublicKey loadPublicKey(String location) throws Exception {
        String key = readKey(location)
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(key);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }

    private String readKey(String location) throws Exception {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("JWT public key location must not be blank");
        }
        if (location.startsWith("classpath:")) {
            String classpathName = location.substring("classpath:".length());
            while (classpathName.startsWith("/")) {
                classpathName = classpathName.substring(1);
            }
            ClassPathResource resource = new ClassPathResource(classpathName);
            if (!resource.exists()) {
                throw new IllegalStateException("JWT public key resource not found: " + location);
            }
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        String filesystemPath = location.startsWith("file:")
                ? location.substring("file:".length())
                : location;
        return Files.readString(Path.of(filesystemPath), StandardCharsets.UTF_8);
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    /** Current signing key first, then an optional retiring key during rotation. */
    public List<PublicKey> getPreviousPublicKeys() {
        return previousPublicKeys;
    }
}
