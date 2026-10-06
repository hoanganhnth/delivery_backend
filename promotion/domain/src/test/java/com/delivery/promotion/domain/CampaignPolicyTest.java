package com.delivery.promotion.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThat;

class CampaignPolicyTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 1, 0, 0);

    static Stream<Arguments> invalid() {
        return Stream.of(
            bad("code", null, "Voucher code is required"), bad("code", " ", "Voucher code is required"),
            bad("name", null, "Voucher name is required"), bad("name", " ", "Voucher name is required"),
            bad("creator", null, "Voucher creatorType is required"),
            bad("reward", null, "Voucher rewardType is required"),
            bad("scope", null, "Voucher scopeType is required"),
            bad("discount", null, "Voucher discountValue must be non-negative"),
            bad("discount", BigDecimal.valueOf(-1), "Voucher discountValue must be non-negative"),
            bad("cap", BigDecimal.valueOf(-1), "Voucher maxDiscountValue must be non-negative"),
            bad("quantity", null, "Voucher totalQuantity must be positive"), bad("quantity", 0, "Voucher totalQuantity must be positive"),
            bad("limit", null, "Voucher usageLimitPerUser must be positive"), bad("limit", 0, "Voucher usageLimitPerUser must be positive"),
            bad("start", null, "Voucher time window is required"), bad("end", null, "Voucher time window is required"),
            bad("end", START, "Voucher startTime must be before endTime"),
            bad("end", START.minusSeconds(1), "Voucher startTime must be before endTime"),
            bad("minimum", null, "Voucher minOrderValue must be non-negative"),
            bad("minimum", BigDecimal.valueOf(-1), "Voucher minOrderValue must be non-negative"),
            bad("creator", Voucher.CreatorType.MERCHANT, "Voucher campaigns must be platform or shop owned"),
            bad("scope", Voucher.ScopeType.CATEGORY, "Voucher scope must be ALL or SHOP"),
            bad("scope", Voucher.ScopeType.SHOP, "Voucher scopeRefId must identify a restaurant"),
            bad(Map.of("scope", Voucher.ScopeType.SHOP, "ref", 0L), "Voucher scopeRefId must identify a restaurant"),
            bad("ref", 1L, "Platform voucher must not carry scopeRefId"),
            bad("creator", Voucher.CreatorType.SHOP, "Shop voucher requires restaurantId and ownerPrincipalId"),
            bad(shop("restaurant", null), "Shop voucher requires restaurantId and ownerPrincipalId"),
            bad(shop("restaurant", 0L), "Shop voucher requires restaurantId and ownerPrincipalId"),
            bad(shop("owner", null), "Shop voucher requires restaurantId and ownerPrincipalId"),
            bad(shop("owner", 0L), "Shop voucher requires restaurantId and ownerPrincipalId"),
            bad(shop("reward", Voucher.RewardType.FREESHIP), "Shop vouchers cannot fund freeship"),
            bad(shop("scope", Voucher.ScopeType.ALL, "ref", null), "Shop voucher must target its restaurant"),
            bad(shop("ref", 3L), "Shop voucher must target its restaurant"),
            bad(Map.of("reward", Voucher.RewardType.FREESHIP, "scope", Voucher.ScopeType.SHOP, "ref", 1L), "Freeship voucher must be platform-wide"),
            bad("layer", "typo", "Voucher layer is invalid"),
            bad(Map.of("reward", Voucher.RewardType.FREESHIP, "layer", "PLATFORM_DISCOUNT"), "Freeship reward requires the FREESHIP layer"),
            bad("layer", "FREESHIP", "FREESHIP layer requires a freeship reward"),
            bad(shop("layer", "PLATFORM_DISCOUNT"), "Shop voucher requires the SHOP_DISCOUNT layer"),
            bad("layer", "SHOP_DISCOUNT", "Platform voucher cannot use the SHOP_DISCOUNT layer")
        );
    }

    @ParameterizedTest @MethodSource("invalid")
    void validationReturnsExactFirstMessage(Map<String,Object> overrides, String message) {
        var outcome = CampaignPolicy.create(campaign(overrides));
        assertThat(outcome.failure()).isExactlyInstanceOf(IllegalArgumentException.class).hasMessage(message);
        assertThat(outcome.layer()).isNull();
        if (!message.equals("Voucher layer is invalid")) assertThat(outcome.failure().getCause()).isNull();
    }

    @Test void invalidLayerRetainsEnumCause() {
        var failure = CampaignPolicy.create(campaign(Map.of("layer", "typo"))).failure();
        assertThat(failure).hasMessage("Voucher layer is invalid");
        assertThat(failure.getCause()).isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("No enum constant com.delivery.promotion.domain.VoucherLayer.TYPO");
    }

    @Test void nullRequestAndMultipleInvalidFieldsRetainCheckOrder() {
        assertThat(CampaignPolicy.create(null).failure()).hasMessage("Create voucher request is required");
        var all = new HashMap<String,Object>();
        for (String key : new String[]{"code", "name", "creator", "reward", "scope", "discount", "quantity", "limit", "start", "minimum"}) all.put(key, null);
        var ordered = new String[][]{{"code","Voucher code is required"},{"name","Voucher name is required"},
                {"creator","Voucher creatorType is required"},{"reward","Voucher rewardType is required"},
                {"scope","Voucher scopeType is required"},{"discount","Voucher discountValue must be non-negative"},
                {"quantity","Voucher totalQuantity must be positive"},{"limit","Voucher usageLimitPerUser must be positive"},
                {"start","Voucher time window is required"},{"minimum","Voucher minOrderValue must be non-negative"}};
        for (var item : ordered) {
            assertThat(CampaignPolicy.create(campaign(all)).failure()).hasMessage(item[1]);
            all.remove(item[0]);
        }
        assertThat(CampaignPolicy.create(campaign(all)).failure()).isNull();
    }

    @Test void defaultsForPlatformShopAndFreeshipAndExplicitLayers() {
        for (String layer : new String[]{null, "", " ", "platform_discount"}) {
            var overrides = new HashMap<String,Object>(); overrides.put("layer", layer);
            var platform = CampaignPolicy.create(campaign(overrides));
            assertThat(platform.failure()).isNull();
            assertThat(platform.layer()).isEqualTo("PLATFORM_DISCOUNT");
            assertThat(platform.fundingSource()).isEqualTo("PLATFORM");
            assertThat(platform.approvalStatus()).isEqualTo("APPROVED");
            assertThat(platform.active()).isTrue(); assertThat(platform.restaurantId()).isNull();
        }
        for (String layer : new String[]{null, "", "shop_discount"}) {
            var shop = CampaignPolicy.create(campaign(shop("layer", layer)));
            assertThat(shop.failure()).isNull(); assertThat(shop.layer()).isEqualTo("SHOP_DISCOUNT");
            assertThat(shop.fundingSource()).isEqualTo("SHOP"); assertThat(shop.approvalStatus()).isEqualTo("PENDING");
            assertThat(shop.active()).isFalse(); assertThat(shop.restaurantId()).isEqualTo(1L);
        }
        for (String layer : new String[]{null, "", "freeship"}) {
            var overrides = new HashMap<String,Object>(); overrides.put("layer", layer); overrides.put("reward", Voucher.RewardType.FREESHIP);
            var freeship = CampaignPolicy.create(campaign(overrides));
            assertThat(freeship.failure()).isNull(); assertThat(freeship.layer()).isEqualTo("FREESHIP");
        }
        assertThat(CampaignPolicy.create(campaign(Map.of("scope", Voucher.ScopeType.SHOP, "ref", 2L))).restaurantId()).isEqualTo(2L);
        assertThat(CampaignPolicy.create(campaign(Map.of("cap", BigDecimal.ZERO, "reward", Voucher.RewardType.PERCENTAGE))).failure()).isNull();
    }

    @Test void trimmedLayerValidationStillStoresUntrimmedUppercase() {
        var result = CampaignPolicy.create(campaign(Map.of("layer", " platform_discount ")));
        assertThat(result.failure()).isNull(); assertThat(result.layer()).isEqualTo(" PLATFORM_DISCOUNT ");
    }

    private static Arguments bad(String key, Object value, String message) {
        var map = new HashMap<String,Object>(); map.put(key, value); return bad(map, message);
    }
    private static Arguments bad(Map<String,Object> map, String message) {
        // Invalid layer is tested separately because it intentionally carries a cause.
        return Arguments.of(map, message);
    }
    private static Map<String,Object> shop(Object... overrides) {
        var map = new HashMap<String,Object>();
        map.put("creator", Voucher.CreatorType.SHOP); map.put("scope", Voucher.ScopeType.SHOP);
        map.put("ref", 1L); map.put("restaurant", 1L); map.put("owner", 2L);
        for (int i=0; i<overrides.length; i+=2) map.put((String)overrides[i], overrides[i+1]);
        return map;
    }
    private static CampaignPolicy.Campaign campaign(Map<String,Object> values) {
        return new CampaignPolicy.Campaign((String)values.getOrDefault("code", "CODE"),
            (String)values.getOrDefault("name", "Name"),
            (Voucher.CreatorType)values.getOrDefault("creator", Voucher.CreatorType.PLATFORM),
            (Voucher.RewardType)values.getOrDefault("reward", Voucher.RewardType.FIXED),
            (Voucher.ScopeType)values.getOrDefault("scope", Voucher.ScopeType.ALL),
            (BigDecimal)values.getOrDefault("discount", BigDecimal.ZERO), (BigDecimal)values.get("cap"),
            (Integer)values.getOrDefault("quantity", 1), (Integer)values.getOrDefault("limit", 1),
            (LocalDateTime)values.getOrDefault("start", START), (LocalDateTime)values.getOrDefault("end", START.plusDays(1)),
            (BigDecimal)values.getOrDefault("minimum", BigDecimal.ZERO), (Long)values.get("ref"),
            (Long)values.get("restaurant"), (Long)values.get("owner"), (String)values.get("layer"));
    }
}
