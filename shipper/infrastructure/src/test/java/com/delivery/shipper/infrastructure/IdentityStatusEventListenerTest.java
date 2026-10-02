package com.delivery.shipper.infrastructure;

import com.delivery.identity.contracts.IdentityLifecycleStatus;
import com.delivery.identity.contracts.IdentityStatusChanged;
import com.delivery.shipper.application.api.ShipperCommands;
import com.delivery.shipper.application.api.ShipperUseCases;
import com.delivery.shipper.infrastructure.adapter.IdentityStatusEventListener;
import com.delivery.shipper.infrastructure.entity.IdentityInboxReceipt;
import com.delivery.shipper.infrastructure.repository.IdentityInboxReceiptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class IdentityStatusEventListenerTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ShipperUseCases.ProjectIdentityStatus projection = mock(ShipperUseCases.ProjectIdentityStatus.class);
    private final IdentityInboxReceiptRepository receipts = mock(IdentityInboxReceiptRepository.class);
    private final IdentityStatusEventListener listener = new IdentityStatusEventListener(mapper, projection, receipts);

    @Test void passesBusinessDecisionToApplicationAndPreservesCanonicalReceipt() throws Exception {
        UUID id = UUID.randomUUID();
        listener.statusChanged(raw(id, 7L, 2));
        verify(projection).execute(new ShipperCommands.IdentityStatusProjection(7, "BLOCKED", 2));
        verify(receipts).save(argThat(receipt -> id.equals(receipt.getEventId())
                && IdentityStatusChanged.TYPE.equals(receipt.getEventType())
                && receipt.getPrincipalId() == 7 && receipt.getPayloadFingerprint().length() == 64));
    }

    @Test void exactReplaySkipsApplicationWhileConflictingReuseFailsClosed() throws Exception {
        UUID id = UUID.randomUUID();
        String raw = raw(id, 7L, 2);
        IdentityInboxReceipt previous = new IdentityInboxReceipt();
        previous.setEventId(id); previous.setEventType(IdentityStatusChanged.TYPE); previous.setPrincipalId(7L);
        previous.setPayloadFingerprint(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        when(receipts.findById(id)).thenReturn(Optional.of(previous));
        listener.statusChanged(raw);
        verifyNoInteractions(projection);
        verify(receipts, never()).save(any());
        assertThatThrownBy(() -> listener.statusChanged(raw(id, 7L, 3))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Conflicting identity event reuse");
    }

    @Test void invalidIdentityEnvelopeCannotTouchPersistenceOrApplication() throws Exception {
        assertThatThrownBy(() -> listener.statusChanged(raw(UUID.randomUUID(), null, 2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listener.statusChanged(raw(UUID.randomUUID(), 7L, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(projection, receipts);
    }

    private String raw(UUID id, Long principal, long version) throws Exception {
        return mapper.writeValueAsString(new IdentityStatusChanged(id, IdentityStatusChanged.TYPE, 1,
                Instant.EPOCH, id, null, principal, IdentityLifecycleStatus.BLOCKED, version, "TEST", null));
    }
}
