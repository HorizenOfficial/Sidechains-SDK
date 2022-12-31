package com.horizen.secret;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.node.NodeWallet;
import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.node.NodeWalletBase;
import scorex.crypto.hash.Blake2b256;
import com.horizen.cryptolibprovider.VrfFunctions.KeyType;
import java.util.EnumMap;
import java.util.List;

public class VrfKeyGenerator implements SecretCreator<VrfSecretKey> {
    private static VrfKeyGenerator instance;

    static {
        instance = new VrfKeyGenerator();
    }

    private VrfKeyGenerator() {
        super();
    }

    public static VrfKeyGenerator getInstance() {
        return instance;
    }


    @Override
    public VrfSecretKey generateSecret(byte[] seed) {
        EnumMap<KeyType, byte[]> keys = CryptoLibProvider.vrfFunctions().generatePublicAndSecretKeys(seed);
        return new VrfSecretKey(keys.get(KeyType.SECRET), keys.get(KeyType.PUBLIC));
    }

    @Override
    public byte[] salt() {
        return "VrfKey".getBytes();
    }
}
