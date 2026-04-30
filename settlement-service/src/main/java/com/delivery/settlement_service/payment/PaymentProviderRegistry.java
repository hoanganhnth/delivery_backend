package com.delivery.settlement_service.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry quản lý tất cả PaymentProvider.
 * Spring auto-inject tất cả bean implement PaymentProvider.
 */
@Component
@Slf4j
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> providers = new HashMap<>();

    /**
     * Spring inject tất cả PaymentProvider beans vào constructor
     */
    public PaymentProviderRegistry(List<PaymentProvider> providerList) {
        for (PaymentProvider provider : providerList) {
            providers.put(provider.getProviderName().toUpperCase(), provider);
            log.info("📦 Registered payment provider: {}", provider.getProviderName());
        }
        log.info("✅ Total payment providers registered: {}", providers.size());
    }

    /**
     * Lấy provider theo tên
     * @throws IllegalArgumentException nếu provider không tồn tại
     */
    public PaymentProvider getProvider(String providerName) {
        PaymentProvider provider = providers.get(providerName.toUpperCase());
        if (provider == null) {
            throw new IllegalArgumentException(
                    "Payment provider '" + providerName + "' not found. Available: " + providers.keySet());
        }
        return provider;
    }

    /**
     * Kiểm tra provider có tồn tại không
     */
    public boolean hasProvider(String providerName) {
        return providers.containsKey(providerName.toUpperCase());
    }

    /**
     * Danh sách tên provider available
     */
    public java.util.Set<String> getAvailableProviders() {
        return providers.keySet();
    }
}
