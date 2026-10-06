package com.delivery.livestream.application;
import com.delivery.livestream.api.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProductUseCasesTest {
    private final UUID id=UUID.randomUUID();
    @SuppressWarnings("unchecked") private final ProductPorts<String,String,String,String> ports=mock(ProductPorts.class);
    private final ProductUseCases<String,String,String,String> useCases=new ProductUseCases<>(ports);
    private void room(String status) { when(ports.room(id)).thenReturn("room"); when(ports.roomSnapshot("room")).thenReturn(new RoomSnapshot(id,7L,42L,status,0L)); }
    private void product(Boolean pinned) { when(ports.findOrCreate(id,11L)).thenReturn("product"); when(ports.snapshot("product")).thenReturn(new ProductSnapshot(1L,11L,42L,BigDecimal.ONE,pinned)); }
    @Test void pinOrdersPermissionScopeStatusDuplicatePriceAuthoritySaveEventResponse() {
        room("CREATED"); product(false); when(ports.pin("product","room","command")).thenReturn("saved"); when(ports.response("saved")).thenReturn("response");
        assertThat(useCases.pin(id,"command",11L,42L,7L,false)).isEqualTo("response");
        var order=inOrder(ports); order.verify(ports).room(id); order.verify(ports,times(2)).roomSnapshot("room");
        order.verify(ports).findOrCreate(id,11L); order.verify(ports).snapshot("product"); order.verify(ports).price("product","command");
        order.verify(ports).pin("product","room","command"); order.verify(ports).pinned(id,"saved","command"); order.verify(ports).response("saved");
    }
    @Test void rejectsInOriginalOrder() {
        room("ENDED"); product(true);
        assertThatThrownBy(() -> useCases.pin(id,"command",11L,43L,8L,false)).hasMessage("Bạn không có quyền thao tác với livestream này");
        assertThatThrownBy(() -> useCases.pin(id,"command",11L,43L,7L,false)).hasMessage("Sản phẩm không thuộc restaurant của livestream");
        assertThatThrownBy(() -> useCases.pin(id,"command",11L,42L,7L,false)).hasMessageContaining("Chỉ có thể thêm");
        verify(ports,never()).findOrCreate(any(),any());
        room("LIVE"); assertThatThrownBy(() -> useCases.pin(id,"command",11L,null,8L,true)).hasMessage("Sản phẩm đã được pin trong livestream");
        verify(ports,never()).price(any(),any());
    }
    @Test void unpinAndRemoveRetainAdminBypassAndLegacySellerDeletion() {
        room("LIVE"); when(ports.find(id,11L)).thenReturn("product");
        useCases.unpin(id,11L,8L,true); useCases.unpin(id,11L,8L,true); useCases.remove(id,11L,7L,false);
        verify(ports,times(2)).unpin("product"); verify(ports,times(2)).unpinned(id,11L); verify(ports).remove("product",7L);
        var order=inOrder(ports); order.verify(ports).unpin("product"); order.verify(ports).unpinned(id,11L);
    }
    @Test void unpinAndRemoveRejectStatusBeforeProductLookup() {
        room("ENDED");
        assertThatThrownBy(() -> useCases.unpin(id,11L,7L,false)).hasMessageContaining("Chỉ có thể bỏ");
        assertThatThrownBy(() -> useCases.remove(id,11L,7L,false)).hasMessageContaining("Chỉ có thể xóa");
        verify(ports,never()).find(any(),any());
    }
    @Test void authorityFailureDoesNotPublish() {
        room("LIVE"); product(null); when(ports.pin("product","room","command")).thenThrow(new IllegalStateException("authority"));
        assertThatThrownBy(() -> useCases.pin(id,"command",11L,null,7L,false)).hasMessage("authority");
        verify(ports).price("product","command"); verify(ports,never()).pinned(any(),any(),any());
    }
    @Test void listRetainsBoundsAndDoesNotRequireRoomExistence() {
        for(boolean pinned:List.of(false,true)) { when(ports.list(id,pinned,100)).thenReturn(List.of("product")); when(ports.response("product")).thenReturn("response"); assertThat(useCases.list(id,pinned)).containsExactly("response"); }
        verify(ports,never()).room(any());
    }
}
