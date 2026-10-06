package com.delivery.livestream.application;
import com.delivery.livestream.api.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ModerationUseCaseTest {
    private final UUID id=UUID.randomUUID();
    @SuppressWarnings("unchecked") private final ModerationPorts<String> ports=mock(ModerationPorts.class);
    private final ModerationUseCase<String> useCase=new ModerationUseCase<>(ports);
    @Test void allActionsPrecedeTrimmedAuditAndRepeatAudits() {
        when(ports.audit(id,1L,"WARN","reason",null)).thenReturn("response");
        assertThat(useCase.moderate(id,1L,7L,true,"WARN","  reason  ",null)).isEqualTo("response");
        useCase.moderate(id,1L,7L,true,"WARN","reason",null);
        useCase.moderate(id,1L,7L,true,"FORCE_END","reason",null);
        useCase.moderate(id,1L,7L,true,"UNPIN","reason",11L);
        verify(ports,times(2)).audit(id,1L,"WARN","reason",null);
        var order=inOrder(ports); order.verify(ports).inspect(id); order.verify(ports).audit(id,1L,"WARN","reason",null);
        order.verify(ports).inspect(id); order.verify(ports).audit(id,1L,"WARN","reason",null);
        order.verify(ports).end(id,7L); order.verify(ports).audit(id,1L,"FORCE_END","reason",null);
        order.verify(ports).unpin(id,11L,7L); order.verify(ports).audit(id,1L,"UNPIN","reason",11L);
    }
    @Test void permissionBeforeActionAndActionFailureBeforeAudit() {
        assertThatThrownBy(() -> useCase.moderate(id,1L,7L,false,"WARN","reason",null)).hasMessage("ADMIN role is required for moderation"); verifyNoInteractions(ports);
        doThrow(new IllegalStateException("ended")).when(ports).end(id,7L);
        assertThatThrownBy(() -> useCase.moderate(id,1L,7L,true,"FORCE_END","reason",null)).hasMessage("ended"); verify(ports,never()).audit(any(),any(),any(),any(),any());
        assertThatThrownBy(() -> useCase.moderate(id,1L,7L,true,"UNKNOWN","reason",null)).isInstanceOf(IllegalArgumentException.class);
    }
}
