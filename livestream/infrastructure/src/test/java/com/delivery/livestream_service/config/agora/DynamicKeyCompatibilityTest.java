package com.delivery.livestream_service.config.agora;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

/** Fixed wire vectors independently calculated with little-endian fields and HMAC-SHA1. */
class DynamicKeyCompatibilityTest {
    private static final String APP = "0".repeat(32);
    private static final String CERTIFICATE = "1".repeat(32);
    private static final int ISSUED = 1_700_000_000;
    private static final int EXPIRES = 1_700_003_600;
    private static final long UID = 0xffff_ffffL;

    @ParameterizedTest
    @CsvSource({
            "1,935C79D44A4B977A3DB01DE0AE341D000C83B3E0",
            "2,550F19CEB377BE1DAF062ED16418C623A7B3FA85",
            "3,24D3465C40666DFC5BF23D8423170AD2AA9E4061",
            "4,59D607F6277FA85B275BEC1E2D0889F768B57732"
    })
    void legacyKeysPreserveServiceExpiryPermissionAndUnsignedUidSignature(short service, String expectedSignature) throws Exception {
        String wire = switch (service) {
            case 1 -> DynamicKey5.generateMediaChannelKey(APP, CERTIFICATE, "room", ISSUED, 42, UID, EXPIRES);
            case 2 -> DynamicKey5.generateRecordingKey(APP, CERTIFICATE, "room", ISSUED, 42, UID, EXPIRES);
            case 3 -> DynamicKey5.generatePublicSharingKey(APP, CERTIFICATE, "room", ISSUED, 42, UID, EXPIRES);
            case 4 -> DynamicKey5.generateInChannelPermissionKey(APP, CERTIFICATE, "room", ISSUED, 42, UID, EXPIRES,
                    DynamicKey5.audioVideoUpload);
            default -> throw new AssertionError("Unexpected fixture service");
        };
        assertThat(wire).startsWith("005");
        var decoded = new DynamicKey5();
        assertThat(decoded.fromString(wire)).isTrue();
        assertThat(decoded.content.serviceType).isEqualTo(service);
        assertThat(decoded.content.signature).isEqualTo(expectedSignature);
        assertThat(decoded.content.appID).containsExactly(new byte[16]);
        assertThat(decoded.content.unixTs).isEqualTo(ISSUED);
        assertThat(decoded.content.salt).isEqualTo(42);
        assertThat(decoded.content.expiredTs).isEqualTo(EXPIRES);
        assertThat(decoded.content.extra).isEqualTo(service == 4 ? Map.of((short) 1, "3") : Map.of());
        assertThat(DynamicKey5.generateSignature(CERTIFICATE.toUpperCase(), service, APP.toUpperCase(),
                ISSUED, 42, "room", UID, EXPIRES, decoded.content.extra)).isEqualTo(expectedSignature);
        assertThat(DynamicKey5.generateSignature(CERTIFICATE, service, APP, ISSUED, 42, "other-room",
                UID, EXPIRES, decoded.content.extra)).isNotEqualTo(expectedSignature);
    }

    @Test void emptyPayloadAndOtherVersionsAreRejected() {
        var decoded = new DynamicKey5();
        assertThat(decoded.fromString("004payload")).isFalse();
        assertThat(decoded.fromString("005")).isFalse();
        assertThat(decoded.content).isNull();
    }

    @Test void originalDynamicKeyHasFixedWidthTimestampSaltAndKnownSignature() throws Exception {
        assertThat(DynamicKey.generate(APP, CERTIFICATE, "room", ISSUED, 42)).isEqualTo(
                "55bca94625160bf97d3462f082d70cb956c78eab" + APP + "1700000000" + "0000002a");
        assertThat(DynamicKey.generate(APP, CERTIFICATE, "room", 1, 0))
                .endsWith(APP + "0000000001" + "00000000");
        assertThat(DynamicKey.generate(APP, CERTIFICATE, "other-room", ISSUED, 42))
                .doesNotStartWith("55bca94625160bf97d3462f082d70cb956c78eab");
    }
}
