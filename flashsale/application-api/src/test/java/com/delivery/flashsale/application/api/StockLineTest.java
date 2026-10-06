package com.delivery.flashsale.application.api;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class StockLineTest {
    @Test
    void snapshotRetainsServerItemQuantityAndPrice() {
        var line = new StockPort.Line(1L, 11L, 2, new BigDecimal("50.00"));
        assertThat(line.itemId()).isEqualTo(1L);
        assertThat(line.menuItemId()).isEqualTo(11L);
        assertThat(line.quantity()).isEqualTo(2);
        assertThat(line.price()).isEqualTo(new BigDecimal("50.00"));
        assertThat(line).isEqualTo(new StockPort.Line(1L, 11L, 2, new BigDecimal("50.00")));
    }
}
