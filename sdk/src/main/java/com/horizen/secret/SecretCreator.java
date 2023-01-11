package com.horizen.secret;

import com.horizen.node.NodeWallet;
import com.horizen.node.NodeWalletBase;

public interface SecretCreator<S extends Secret>
{
    // Generate secret without context of previously generated secrets stored in wallet.
    // Mostly for tests.
    S generateSecret(byte[] seed);

    byte[] salt();
}