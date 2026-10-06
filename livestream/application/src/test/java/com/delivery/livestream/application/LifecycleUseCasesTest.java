package com.delivery.livestream.application;
import com.delivery.livestream.api.*;
import com.delivery.livestream.domain.LivestreamPolicy.Rejection;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class LifecycleUseCasesTest {
    private final UUID id=UUID.randomUUID();
    @SuppressWarnings("unchecked") private final LifecyclePorts<String,String,String,String,String,String> ports=mock(LifecyclePorts.class);
    private final LifecycleUseCases<String,String,String,String,String,String> useCases=new LifecycleUseCases<>(ports);
    private void room(String status) { when(ports.find(id)).thenReturn("room"); when(ports.snapshot("room")).thenReturn(new RoomSnapshot(id,7L,42L,status,null)); }
    @Test void createAndReadsDelegateWithFixedBound() {
        when(ports.create("command",7L)).thenReturn("room"); when(ports.response("room")).thenReturn("response");
        assertThat(useCases.create("command",7L,"AGORA")).isEqualTo("response");
        room("LIVE"); assertThat(useCases.inspect(id)).isEqualTo("response");
        for(String filter:List.of("active","seller","restaurant")) {
            when(ports.list(filter,7L,100)).thenReturn(List.of("room"));
            assertThat(useCases.list(filter,7L)).containsExactly("response");
        }
    }
    @Test void unsupportedProviderNeverWrites() {
        assertThatThrownBy(() -> useCases.create("command",7L,"LIVEKIT")).isInstanceOf(Rejection.class);
        verifyNoInteractions(ports);
    }
    @Test void startSavesBeforeTokenAndPublishesBeforeResponse() {
        room("CREATED"); when(ports.start("room")).thenReturn("saved"); when(ports.token(id,9L,"HOST",3600)).thenReturn("token");
        when(ports.startResponse("saved","token",9)).thenReturn("response");
        assertThat(useCases.start(id,9L,"aDmIn")).isEqualTo("response");
        var order=inOrder(ports); order.verify(ports).find(id); order.verify(ports).snapshot("room"); order.verify(ports).start("room");
        order.verify(ports).token(id,9L,"HOST",3600); order.verify(ports).started("saved"); order.verify(ports).startResponse("saved","token",9);
    }
    @Test void permissionPrecedesStatusAndMissingRoomPrecedesPermission() {
        room("ENDED"); assertThatThrownBy(() -> useCases.start(id,9L,"SHOP_OWNER")).hasMessage("Bạn không có quyền thao tác với livestream này");
        assertThatThrownBy(() -> useCases.end(id,9L,null)).hasMessage("Bạn không có quyền thao tác với livestream này");
        verify(ports,never()).start(any()); verify(ports,never()).end(any());
        when(ports.find(id)).thenThrow(new IllegalStateException("missing"));
        assertThatThrownBy(() -> useCases.start(id,9L,"ADMIN")).hasMessage("missing");
    }
    @Test void invalidTransitionsNeverWriteOrIssueToken() {
        room("LIVE"); assertThatThrownBy(() -> useCases.start(id,7L,"SHOP_OWNER")).isInstanceOf(Rejection.class);
        room("CREATED"); assertThatThrownBy(() -> useCases.end(id,7L,"SHOP_OWNER")).isInstanceOf(Rejection.class);
        assertThatThrownBy(() -> useCases.join(id,8L,true)).isInstanceOf(Rejection.class);
        verify(ports,never()).token(any(),any(),any(),anyInt()); verify(ports,never()).count(any(),anyLong());
    }
    @Test void endSavesBeforeEventAndMapsSavedHandle() {
        room("LIVE"); when(ports.end("room")).thenReturn("saved"); when(ports.response("saved")).thenReturn("response");
        assertThat(useCases.end(id,7L,"SHOP_OWNER")).isEqualTo("response");
        var order=inOrder(ports); order.verify(ports).end("room"); order.verify(ports).ended("saved"); order.verify(ports).response("saved");
    }
    @Test void countedJoinAndMonitoringKeepLegacyWriteAndTokenOrdering() {
        room("LIVE"); when(ports.count("room",1)).thenReturn("saved"); when(ports.token(id,8L,"VIEWER",3600)).thenReturn("token");
        when(ports.joinResponse("saved","token",8)).thenReturn("response");
        assertThat(useCases.join(id,8L,true)).isEqualTo("response");
        var order=inOrder(ports); order.verify(ports).count("room",1); order.verify(ports).token(id,8L,"VIEWER",3600); order.verify(ports).joinResponse("saved","token",8);
        clearInvocations(ports); useCases.join(id,8L,false); verify(ports,never()).count(any(),anyLong()); verify(ports).joinResponse("room","token",8);
    }
    @Test void tokenFailureDoesNotPublishButDoesFollowSave() {
        room("CREATED"); when(ports.start("room")).thenReturn("saved"); when(ports.token(id,7L,"HOST",3600)).thenThrow(new IllegalStateException("token"));
        assertThatThrownBy(() -> useCases.start(id,7L,"SHOP_OWNER")).hasMessage("token");
        verify(ports).start("room"); verify(ports,never()).started(any());
    }
}
