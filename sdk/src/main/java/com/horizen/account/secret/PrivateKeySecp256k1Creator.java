package com.horizen.account.secret;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.account.utils.Secp256k1;
import com.horizen.node.NodeWalletBase;
import com.horizen.secret.Secret;
import com.horizen.secret.SecretCreator;
import sparkz.crypto.hash.Keccak256;
import java.util.List;

public final class PrivateKeySecp256k1Creator implements SecretCreator<PrivateKeySecp256k1> {
    private static PrivateKeySecp256k1Creator instance;

    static {
        instance = new PrivateKeySecp256k1Creator();
    }

    private PrivateKeySecp256k1Creator() {
        super();
    }

    public static PrivateKeySecp256k1Creator getInstance() {
        return instance;
    }

    @Override
    public PrivateKeySecp256k1 generateSecret(byte[] seed) {
        var keyPair = Secp256k1.createKeyPair(seed);

        return new PrivateKeySecp256k1(keyPair.getKey());
    }

    @Override
    public PrivateKeySecp256k1 generateNextSecret(NodeWalletBase wallet) {
        List<Secret> prevSecrets = wallet.secretsOfType(PrivateKeySecp256k1.class);
        byte[] nonce = Ints.toByteArray(prevSecrets.size());
        byte[] seed = (byte[]) Keccak256.hash(Bytes.concat(wallet.walletSeed(), nonce));

        return generateSecret(seed);
    }
}
