package com.delivery.livestream.api;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class SnapshotContractTest {
    @Test void preservesNullableLegacyValuesAndOrder() {
        UUID id=UUID.randomUUID();
        var room=new RoomSnapshot(id,7L,42L,null,null);
        assertThat(room.id()).isEqualTo(id); assertThat(room.sellerId()).isEqualTo(7);
        assertThat(room.restaurantId()).isEqualTo(42); assertThat(room.status()).isNull(); assertThat(room.viewCount()).isNull();
        var product=new ProductSnapshot(-1L,11L,42L,BigDecimal.ONE,null);
        assertThat(product.id()).isEqualTo(-1); assertThat(product.productId()).isEqualTo(11);
        assertThat(product.restaurantId()).isEqualTo(42); assertThat(product.price()).isEqualTo(BigDecimal.ONE); assertThat(product.pinned()).isNull();
        var command=new CheckoutCommand(id,42L,List.of(20L,10L));
        assertThat(command.room()).isEqualTo(id); assertThat(command.restaurant()).isEqualTo(42);
        assertThat(command.products()).containsExactly(20L,10L);
    }
}
