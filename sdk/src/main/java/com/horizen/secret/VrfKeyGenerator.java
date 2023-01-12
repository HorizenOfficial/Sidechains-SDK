package com.horizen.secret;

import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.cryptolibprovider.VrfFunctions.KeyType;

import java.util.EnumMap;

public class VrfKeyGenerator implements SecretCreator<VrfSecretKey> {
    private static final VrfKeyGenerator instance;

    private final String domain = "VrfKey";

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
        return domain.getBytes();
    }
}
