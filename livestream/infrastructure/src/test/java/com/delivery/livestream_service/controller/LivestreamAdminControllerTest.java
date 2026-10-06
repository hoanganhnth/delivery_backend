package com.delivery.livestream_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.mapper.LivestreamMapper;
import com.delivery.livestream_service.repository.LivestreamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class LivestreamAdminControllerTest {
    private final LivestreamRepository repository = mock(LivestreamRepository.class);
    private final LivestreamAdminController controller = new LivestreamAdminController(repository, new LivestreamMapper());
    private final AuthenticatedActor admin = new AuthenticatedActor(90L, 9L, "admin@example.test", Set.of("ADMIN"));

    @Test
    void rejectsNonAdminAndInvalidPaginationBeforeDatabaseAccess() {
        assertThatThrownBy(() -> controller.list(null, 0, 20)).isInstanceOf(RuntimeException.class);
        for (String role : List.of("USER", "SHOP_OWNER", "SHIPPER")) {
            var actor = new AuthenticatedActor(1L, 1L, "actor@example.test", Set.of(role));
            assertThatThrownBy(() -> controller.list(actor, 0, 20)).isInstanceOf(RuntimeException.class);
        }
        assertThatThrownBy(() -> controller.list(admin, -1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.list(admin, 0, 101)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void returnsBoundedPageWithStableOrdering() {
        Pageable page = PageRequest.of(1, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<Livestream>(List.of(), page, 20));
        var result = controller.list(admin, 1, 20);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(repository).findAll(page);
    }
}
