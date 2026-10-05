package com.delivery.promotion.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WalletClaimPolicyTest {
    @Test void collectionAndClaimMessagesAreStable() {
        assertThat(WalletClaimPolicy.collectionFailure(false)).isNull();
        assertThat(WalletClaimPolicy.collectionFailure(true)).isEqualTo("Voucher already collected");
        assertThat(WalletClaimPolicy.claimFailure("SAVED")).isNull();
        for (String status : new String[]{null, "RESERVED", "USED", "EXPIRED"}) {
            assertThat(WalletClaimPolicy.claimFailure(status)).isEqualTo("Voucher is already reserved or used");
        }
    }

    @Test void capacityUsesBothCountersNormalizesNullNegativeAndDefaultsLimitToOne() {
        assertThat(WalletClaimPolicy.capacityFailure(null, null, null)).isNull();
        assertThat(WalletClaimPolicy.capacityFailure(-1, -2, 1)).isNull();
        assertThat(WalletClaimPolicy.capacityFailure(0, 0, 1)).isNull();
        assertThat(WalletClaimPolicy.capacityFailure(1, 0, 2)).isNull();
        for (Integer[] values : new Integer[][]{{1,0,null},{0,1,1},{1,1,2},{2,1,2},{0,0,0},{0,0,-1}}) {
            assertThat(WalletClaimPolicy.capacityFailure(values[0], values[1], values[2]))
                    .isEqualTo("Voucher usage limit has been reached");
        }
        // Preserve int arithmetic for malformed historical data; no new saturation policy.
        assertThat(WalletClaimPolicy.capacityFailure(Integer.MAX_VALUE, 1, 1)).isNull();
    }
}
