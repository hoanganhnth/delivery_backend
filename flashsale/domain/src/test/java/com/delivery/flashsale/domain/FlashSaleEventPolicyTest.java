package com.delivery.flashsale.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static com.delivery.flashsale.domain.FlashSaleEventPolicy.*;

class FlashSaleEventPolicyTest {
    @Test void topicNormalizationAndActionOrdering() {
        for (String topic : new String[]{null,""," "}) assertThatThrownBy(()->canonicalSourceTopic(topic)).hasMessage("source topic is required");
        assertThat(canonicalSourceTopic("created-retry-flashsale-123")).isEqualTo("created");
        assertThat(canonicalSourceTopic("created-retry-flashsale-x")).isEqualTo("created-retry-flashsale-x");
        assertThat(actionFor("created","created","cancelled","refund")).isEqualTo("COMMIT");
        assertThat(actionFor("cancelled","created","cancelled","refund")).isEqualTo("RELEASE");
        assertThat(actionFor("refund","created","cancelled","refund")).isEqualTo("RELEASE");
        assertThat(actionFor("same","same","same","same")).isEqualTo("COMMIT");
        assertThatThrownBy(()->actionFor("other","created","cancelled","refund")).hasMessage("Unexpected flash-sale reservation source topic: other");
    }
    @Test void everyReceiptFingerprintFieldMustMatch() {
        UUID id=UUID.randomUUID(); var stored=new Receipt("created","COMMIT",1L,id,"hash");
        requireExactReplay(stored,new Receipt("created","COMMIT",1L,id,"hash"));
        requireExactReplay(new Receipt("created","COMMIT",1L,null,"hash"),new Receipt("created","COMMIT",1L,null,"hash"));
        for (Receipt incoming : java.util.List.of(new Receipt("cancelled","COMMIT",1L,id,"hash"),
                new Receipt("created","RELEASE",1L,id,"hash"),new Receipt("created","COMMIT",2L,id,"hash"),
                new Receipt("created","COMMIT",1L,null,"hash"),new Receipt("created","COMMIT",1L,id,"changed")))
            assertThatThrownBy(()->requireExactReplay(stored,incoming)).hasMessage("eventId replay has a contradictory flash-sale reservation payload");
    }
    @Test void outboxIdentityRetryBoundariesAndErrorTruncation() {
        UUID id=UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertThat(eventType("RESERVED")).isEqualTo("FLASH_SALE_RESERVATION_RESERVED");
        assertThat(eventId(id,eventType("RESERVED"))).isEqualTo(UUID.nameUUIDFromBytes((id+":FLASH_SALE_RESERVATION_RESERVED").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(eventId(id,eventType("COMMITTED"))).isNotEqualTo(eventId(id,eventType("RESERVED")));
        assertThat(dead(11)).isFalse(); assertThat(dead(12)).isTrue(); assertThat(dead(13)).isTrue();
        for (int attempt=1;attempt<=11;attempt++) assertThat(retrySeconds(attempt)).isEqualTo(1L<<Math.min(attempt,8));
        assertThat(lastError(null)).isEqualTo("Kafka publish failed"); assertThat(lastError("")).isEmpty();
        assertThat(lastError("x".repeat(2001))).hasSize(2000);
    }
}
