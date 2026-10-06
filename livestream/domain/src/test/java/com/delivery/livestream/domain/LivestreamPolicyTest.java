package com.delivery.livestream.domain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static com.delivery.livestream.domain.LivestreamPolicy.*;
class LivestreamPolicyTest {
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"CREATED","LIVE","ENDED","CANCELLED"})
    void transitions(String status) {
        if ("CREATED".equals(status)) start(status); else rejected(() -> start(status), Failure.STATUS, "Không thể bắt đầu livestream. Trạng thái hiện tại: " + status);
        if ("LIVE".equals(status)) { end(status); join(status); renewalStatus(status); }
        else {
            rejected(() -> end(status), Failure.STATUS, "Không thể kết thúc livestream. Trạng thái hiện tại: " + status);
            rejected(() -> join(status), Failure.STATUS, "Livestream chưa bắt đầu hoặc đã kết thúc. Trạng thái: " + status);
            rejected(() -> renewalStatus(status), Failure.STATUS, "Chỉ gia hạn token cho livestream đang phát");
        }
        for (String verb : List.of("thêm","bỏ","xóa")) {
            if ("LIVE".equals(status) || "CREATED".equals(status)) productStatus(status, verb);
            else rejected(() -> productStatus(status, verb), Failure.STATUS, "Chỉ có thể " + verb + " sản phẩm khi livestream đang chuẩn bị hoặc đang diễn ra");
        }
        token("HOST", status); token(null, status);
        if ("LIVE".equals(status)) token("VIEWER",status);
        else rejected(() -> token("VIEWER",status), Failure.STATUS, "Livestream chưa bắt đầu. Không thể tạo token cho viewer.");
        for (boolean context : List.of(false,true)) {
            if ("LIVE".equals(status)) {
                checkoutRoom(status, 1L, 1L, context);
                rejected(() -> checkoutRoom(status, 1L, 2L, context), Failure.PERMISSION,"Restaurant không thuộc livestream");
            } else rejected(() -> checkoutRoom(status,1L,2L,context),Failure.STATUS,context ? "Checkout chỉ áp dụng khi phòng đang LIVE" : "Giá livestream chỉ áp dụng khi phòng đang LIVE");
        }
    }
    @Test void permissionsAndNullableValues() {
        provider("AGORA");
        for (String value : new String[]{null,"LIVEKIT",""}) rejected(() -> provider(value),Failure.STATUS,"Chỉ hỗ trợ Agora");
        seller(1L,1L,false); seller(1L,2L,true); seller(null,null,true);
        rejected(() -> seller(1L,2L,false),Failure.PERMISSION,"Bạn không có quyền thao tác với livestream này");
        rejected(() -> seller(1L,null,false),Failure.PERMISSION,"Bạn không có quyền thao tác với livestream này");
        assertThatThrownBy(() -> seller(null,1L,false)).isInstanceOf(NullPointerException.class);
        productScope(1L,null); productScope(1L,1L);
        rejected(() -> productScope(1L,2L),Failure.PERMISSION,"Sản phẩm không thuộc restaurant của livestream");
        duplicate(null); duplicate(false);
        rejected(() -> duplicate(true),Failure.DUPLICATE,"Sản phẩm đã được pin trong livestream");
        assertThat(increment(null)).isEqualTo(1); assertThat(increment(4L)).isEqualTo(5);
        // Preserve unchecked legacy int narrowing and cumulative long overflow.
        assertThat(uid(4294967297L)).isEqualTo(1); assertThat(increment(Long.MAX_VALUE)).isEqualTo(Long.MIN_VALUE);
        assertThatThrownBy(() -> uid(null)).isInstanceOf(NullPointerException.class);
        assertThat(tokenTtl()).isEqualTo(3600);
        for (boolean admin : List.of(false,true)) for(boolean shop : List.of(false,true)) {
            assertThat(renewalRole(1L,1L,admin,shop)).isEqualTo(admin || shop ? "HOST":"VIEWER");
            assertThat(renewalRole(2L,1L,admin,shop)).isEqualTo("VIEWER");
        }
        moderator(1L,true);
        rejected(() -> moderator(null,true),Failure.PERMISSION,"ADMIN role is required for moderation");
        rejected(() -> moderator(1L,false),Failure.PERMISSION,"ADMIN role is required for moderation");
        completePins(2,2);
        rejected(() -> completePins(1,2),Failure.PERMISSION,"Một hoặc nhiều sản phẩm livestream không còn khả dụng");
        replay("a","a");
        assertThatThrownBy(() -> replay("a","b")).isInstanceOf(IllegalArgumentException.class).hasMessage("Idempotency key was already used for another checkout context");
    }
    @Test void hostPermissionCombinations() {
        for (boolean admin : List.of(false, true)) for (boolean shop : List.of(false, true)) {
            rejected(() -> hostRequiresOwnership(null, admin, shop), Failure.PERMISSION, "ADMIN or SHOP_OWNER role is required");
            if (admin || shop) assertThat(hostRequiresOwnership(7L, admin, shop)).isEqualTo(!admin);
            else rejected(() -> hostRequiresOwnership(7L, admin, shop), Failure.PERMISSION, "ADMIN or SHOP_OWNER role is required");
        }
    }
    @Test void fingerprintRetainsOrderedLegacyBytes() {
        UUID room=UUID.fromString("00000000-0000-4000-8000-000000000001");
        assertThat(CheckoutFingerprint.of(room,42L,List.of(20L,10L),7L)).isEqualTo("35bd6636908a3e46374e656f5165f232b62ff388e6f2a48fe044959050d58fea");
        assertThat(CheckoutFingerprint.of(room,42L,List.of(10L,20L),7L)).isNotEqualTo(CheckoutFingerprint.of(room,42L,List.of(20L,10L),7L));
    }
    private void rejected(Runnable action, Failure failure, String message) {
        assertThatThrownBy(action::run).isInstanceOf(Rejection.class).hasMessage(message).satisfies(ex -> assertThat(((Rejection) ex).failure()).isEqualTo(failure));
    }
}
