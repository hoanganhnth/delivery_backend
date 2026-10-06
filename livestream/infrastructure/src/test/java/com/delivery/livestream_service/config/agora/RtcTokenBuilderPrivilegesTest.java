package com.delivery.livestream_service.config.agora;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RtcTokenBuilderPrivilegesTest {

    // Synthetic credentials only; these tests never contact Agora.
    private static final String APP_ID = "0".repeat(32);
    private static final String APP_CERTIFICATE = "1".repeat(32);
    private static final String CHANNEL = "unit-test-livestream";
    private static final int PRIVILEGE_EXPIRY = 2_000_000_000;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void subscriberTokensContainOnlyJoinPrivilege(boolean useUid) {
        AccessToken decoded = buildAndDecode(RtcTokenBuilder.Role.Role_Subscriber, useUid);

        assertThat(decoded.message.messages).isEqualTo(Map.of(
                AccessToken.Privileges.kJoinChannel.intValue, PRIVILEGE_EXPIRY));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void publisherTokensRetainJoinAndAllPublishPrivileges(boolean useUid) {
        AccessToken decoded = buildAndDecode(RtcTokenBuilder.Role.Role_Publisher, useUid);

        assertThat(decoded.message.messages).isEqualTo(Map.of(
                AccessToken.Privileges.kJoinChannel.intValue, PRIVILEGE_EXPIRY,
                AccessToken.Privileges.kPublishAudioStream.intValue, PRIVILEGE_EXPIRY,
                AccessToken.Privileges.kPublishVideoStream.intValue, PRIVILEGE_EXPIRY,
                AccessToken.Privileges.kPublishDataStream.intValue, PRIVILEGE_EXPIRY));
    }

    private AccessToken buildAndDecode(RtcTokenBuilder.Role role, boolean useUid) {
        RtcTokenBuilder builder = new RtcTokenBuilder();
        String token = useUid
                ? builder.buildTokenWithUid(APP_ID, APP_CERTIFICATE, CHANNEL, 42, role, PRIVILEGE_EXPIRY)
                : builder.buildTokenWithUserAccount(APP_ID, APP_CERTIFICATE, CHANNEL, "test-account", role,
                        PRIVILEGE_EXPIRY);
        assertThat(token).isNotEmpty();
        AccessToken decoded = new AccessToken(APP_ID, APP_CERTIFICATE, CHANNEL, "");
        assertThat(decoded.fromString(token)).isTrue();
        return decoded;
    }
}
