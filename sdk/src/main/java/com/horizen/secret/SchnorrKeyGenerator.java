package com.horizen.secret;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.cryptolibprovider.utils.SchnorrFunctions.KeyType;
import com.horizen.node.NodeWalletBase;
import scorex.crypto.hash.Blake2b256;

import java.util.EnumMap;
import java.util.List;

public class SchnorrKeyGenerator implements SecretCreator<SchnorrSecret> {
    private static final SchnorrKeyGenerator instance;

    static {
        instance = new SchnorrKeyGenerator();
    }

    private SchnorrKeyGenerator() {
        super();
    }

    public static SchnorrKeyGenerator getInstance() {
        return instance;
    }


    @Override
    public SchnorrSecret generateSecret(byte[] seed) {
        EnumMap<KeyType, byte[]> keys = CryptoLibProvider.schnorrFunctions().generateSchnorrKeys(seed);
        return new SchnorrSecret(keys.get(KeyType.SECRET), keys.get(KeyType.PUBLIC));
    }

    @Override
    public SchnorrSecret generateNextSecret(NodeWalletBase wallet) {
        List<Secret> prevSecrets = wallet.secretsOfType(SchnorrSecret.class);
        byte[] nonce = Ints.toByteArray(prevSecrets.size());
        byte[] seed = (byte[])Blake2b256.hash(Bytes.concat(wallet.walletSeed(), nonce));

        return generateSecret(seed);
    }
}
