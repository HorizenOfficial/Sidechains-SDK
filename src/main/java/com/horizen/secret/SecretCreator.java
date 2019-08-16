package com.horizen.secret;

import com.horizen.node.NodeWallet;

interface SecretCreator<S extends Secret>
{
    // Generate secret without context of previously generated secrets stored in wallet.
    // Mostly for tests.
    S generateSecret(byte[] seed);

    // Generate secret taking in consideration context of previously generated secrets stored in wallet.
    S generateSecretWithContext(NodeWallet wallet);
}