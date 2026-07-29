package com.delivery.api_gateway.config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class JwtPublicKeyProvider {

    private final PublicKey publicKey;

    public JwtPublicKeyProvider(
            @Value("${jwt.public-key.path:}") String publicKeyLocation) {
        try {
            String key = readKey(publicKeyLocation);
            key = key.replaceAll("-----BEGIN [^-]+-----", "")
                     .replaceAll("-----END [^-]+-----", "")
                     .replaceAll("\\s", "");

            byte[] decoded = Base64.getDecoder().decode(key);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            this.publicKey = kf.generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to initialize JWT public key from configured location", e);
        }
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
}
