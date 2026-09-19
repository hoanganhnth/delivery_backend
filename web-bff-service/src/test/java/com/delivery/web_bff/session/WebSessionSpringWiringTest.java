package com.delivery.web_bff.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.delivery.web_bff.auth.AuthGateway;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

class WebSessionSpringWiringTest {
    @Test
    void productionConstructorsAreUnambiguousToSpring() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(WebSessionRepository.class, () -> mock(WebSessionRepository.class));
            context.registerBean(SessionFactory.class, () -> mock(SessionFactory.class));
            context.registerBean(TokenVault.class, () -> mock(TokenVault.class));
            context.registerBean(AuthGateway.class, () -> mock(AuthGateway.class));
            context.registerBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));
            context.register(WebSessionService.class, WebSessionRefreshService.class);

            context.refresh();

            assertThat(context.getBean(WebSessionService.class)).isNotNull();
            assertThat(context.getBean(WebSessionRefreshService.class)).isNotNull();
        }
    }
}
