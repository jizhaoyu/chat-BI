package com.jizhaoyu.chatbi.infrastructure.credential;

public final class CredentialDecryptionException extends IllegalStateException {
    public static final String ERROR_CODE = "CREDENTIAL_DECRYPTION_FAILED";

    public CredentialDecryptionException() {
        super(ERROR_CODE);
    }
}
