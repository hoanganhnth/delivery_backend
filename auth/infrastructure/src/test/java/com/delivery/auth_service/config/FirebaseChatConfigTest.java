package com.delivery.auth_service.config;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FirebaseChatConfigTest {
    private final ResourceLoader loader = mock(ResourceLoader.class);
    private final FirebaseChatConfig config = new FirebaseChatConfig(loader);

    @Test
    void disabledFeatureDoesNotCreateFirebaseBeans() {
        for (String enabled : new String[] {"false", ""}) {
            new ApplicationContextRunner().withUserConfiguration(FirebaseChatConfig.class)
                    .withPropertyValues("app.firebase.chat.enabled=" + enabled)
                    .run(context -> {
                        assertThat(context).doesNotHaveBean("firebaseChatApp")
                                .doesNotHaveBean("firebaseChatAuth").doesNotHaveBean("firebaseChatTokenIssuer");
                    });
        }
    }

    @Test
    void enabledFeatureRequiresAServiceAccountPath() {
        for (String path : new String[] {null, "", " "}) {
            assertThatThrownBy(() -> config.firebaseChatApp(path)).isInstanceOf(IOException.class)
                    .hasMessage("Firebase Chat is enabled but service-account path is empty");
        }
        verifyNoInteractions(loader);
    }

    @Test
    void missingAndUnreadableResourcesFailClosed() throws IOException {
        Resource resource = mock(Resource.class);
        when(loader.getResource("file:key.json")).thenReturn(resource);
        when(resource.exists()).thenReturn(false, true);
        when(resource.isReadable()).thenReturn(false);
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> config.firebaseChatApp("file:key.json")).isInstanceOf(IOException.class)
                    .hasMessage("Firebase service account is not readable: file:key.json");
        }
        verify(resource, never()).getInputStream();
    }
}
