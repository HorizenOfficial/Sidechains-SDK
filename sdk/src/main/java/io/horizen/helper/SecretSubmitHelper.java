package io.horizen.helper;

import com.horizen.secret.Secret;

public interface SecretSubmitHelper {
    void submitSecret(Secret secret) throws IllegalArgumentException;
}
