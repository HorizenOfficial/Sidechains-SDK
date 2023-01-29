package com.horizen.secret;

import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.cryptolibprovider.VrfFunctions.KeyType;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;

public class VrfKeyGenerator implements SecretCreator<VrfSecretKey> {
    private static final VrfKeyGenerator instance;

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
        String domain = "VrfKey";
        return domain.getBytes(StandardCharsets.UTF_8);
    }
}
