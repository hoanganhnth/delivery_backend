package com.delivery.web_bff.domain.session;

public interface TokenProtection {
    String keyVersion();
    String protect(String plaintext);
    String reveal(String protectedValue);
}
