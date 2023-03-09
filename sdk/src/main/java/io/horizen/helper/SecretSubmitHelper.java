package io.horizen.helper;

import io.horizen.secret.Secret;

public interface SecretSubmitHelper {
    void submitSecret(Secret secret) throws IllegalArgumentException;
}
