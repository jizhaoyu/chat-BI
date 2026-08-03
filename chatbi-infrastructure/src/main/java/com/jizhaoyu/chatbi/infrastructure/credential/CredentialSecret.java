package com.jizhaoyu.chatbi.infrastructure.credential;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class CredentialSecret {
    private final String value;

    public CredentialSecret(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static CredentialSecret of(String value) {
        return new CredentialSecret(value);
    }

    public String reveal() {
        return value;
    }

    byte[] utf8Bytes() {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "CredentialSecret[value=<redacted>]";
    }
}
