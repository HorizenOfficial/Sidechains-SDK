package com.horizen.node;

import com.horizen.proposition.Proposition;
import com.horizen.secret.Secret;

import java.util.List;
import java.util.Optional;

public interface NodeWalletBase {
    Optional<Secret> secretByPublicKey(Proposition publicKey);

    List<Secret> allSecrets();

    List<Secret> secretsOfType(Class<? extends Secret> type);

    byte[] walletSeed();
}
