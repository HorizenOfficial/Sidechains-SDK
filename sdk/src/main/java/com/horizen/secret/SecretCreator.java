package com.horizen.secret;

import com.horizen.node.NodeWalletBase;

public interface SecretCreator<S extends Secret>
{
    // Generate secret without context of previously generated secrets stored in wallet.
    // Mostly for tests.
    S generateSecret(byte[] seed);

    // Generate secret taking in consideration context of previously generated secrets stored in wallet.
    S generateNextSecret(NodeWalletBase wallet);
}