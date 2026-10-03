package com.delivery.tracking_service.service;

import com.delivery.tracking.application.api.ShipperIdentityInboxUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShipperIdentityProjectionListenerTest {
    private final ShipperIdentityInboxUseCase inbox = mock(ShipperIdentityInboxUseCase.class);
    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
    private final ShipperIdentityProjectionListener listener = new ShipperIdentityProjectionListener(new ObjectMapper(), inbox);
    private final String raw = "{\"eventId\":\"9e744800-3481-4f62-a25c-8bc1da8f1686\",\"eventType\":\"shipper.identity.upserted\",\"principalId\":100,\"legacyUserId\":200,\"shipperId\":7002,\"mappingVersion\":1}";

    @Test void acknowledgesOnlyAfterInboxReturns() throws Exception {
        listener.upsert(raw, acknowledgment);
        var order = inOrder(inbox, acknowledgment);
        order.verify(inbox).apply(argThat(command -> command.rawPayload().equals(raw) && command.shipperId() == 7002L));
        order.verify(acknowledgment).acknowledge();
    }

    @Test void storageOrConflictFailureRemainsUnacknowledged() {
        doThrow(new IllegalStateException("transaction failed")).when(inbox).apply(any());
        assertThatThrownBy(() -> listener.upsert(raw, acknowledgment)).hasMessage("transaction failed");
        verifyNoInteractions(acknowledgment);
    }

    @Test void invalidJsonRemainsUnacknowledged() {
        assertThatThrownBy(() -> listener.upsert("{broken", acknowledgment)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        verifyNoInteractions(inbox, acknowledgment);
    }
}
