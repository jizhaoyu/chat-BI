package com.jizhaoyu.chatbi.infrastructure.credential;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialVaultConfigurationTest {
    @Test
    void parsesOldKeyRingWithoutExposingKeyMaterial() {
        String first = key((byte) 1);
        String second = key((byte) 2);

        var keys = CredentialVaultConfiguration.parseOldKeys("old-a:" + first + ",old-b:" + second);

        assertThat(keys).extracting(CredentialEncryptionKey::keyId).containsExactly("old-a", "old-b");
        assertThat(keys.toString()).doesNotContain(first, second).contains("<redacted>");
    }

    @Test
    void rejectsMalformedOldKeyRing() {
        assertThatThrownBy(() -> CredentialVaultConfiguration.parseOldKeys("missing-separator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CREDENTIAL_ENCRYPTION_OLD_KEYS_INVALID");
    }

    private static String key(byte marker) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, marker);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
