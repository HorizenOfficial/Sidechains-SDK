package com.horizen.helper;

import com.horizen.secret.Secret;

public interface SecretSubmitHelper {
    void submitTransaction(Secret secret) throws IllegalArgumentException;
}
