package com.delivery.notification_service.service;

import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseWakeMessageFactoryTest {

    @Test
    void configuresHighPriorityAndroidAndContentAvailableApnsWake() {
        Message message = new FirebaseWakeMessageFactory().create(
                "test-token",
                Notification.builder().setTitle("Title").setBody("Body").build(),
                Map.of("notificationId", "71", "type", "MATCH_FOUND"));

        String json = new Gson().toJson(message);

        assertThat(json)
                .contains("\"priority\":\"high\"")
                .contains("\"apns-priority\":\"10\"")
                .contains("\"apns-push-type\":\"alert\"")
                .contains("\"content-available\":1")
                .contains("\"notificationId\":\"71\"")
                .contains("\"type\":\"MATCH_FOUND\"");
    }

    @Test
    void firebaseServiceLogStatementsDoNotIncludeTokenPayloadOrProviderMessage() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/delivery/notification_service/service/FirebaseService.java"));
        Matcher logStatements = Pattern.compile(
                        "log\\.(?:trace|debug|info|warn|error)\\s*\\((?s:.*?)\\);")
                .matcher(source);

        while (logStatements.find()) {
            assertThat(logStatements.group())
                    .doesNotContainPattern(
                            ",\\s*(?:token|fcmToken|data|body|response|e\\.getMessage\\(\\))\\b")
                    .doesNotContain("data.toString()")
                    .doesNotContain("body.toString()");
        }
        assertThat(source)
                .doesNotContain("throw new IllegalStateException(\"Firebase push delivery failed\", e)");
    }
}
