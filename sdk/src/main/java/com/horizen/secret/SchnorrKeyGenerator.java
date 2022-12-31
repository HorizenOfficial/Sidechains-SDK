package com.horizen.secret;

import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.cryptolibprovider.SchnorrFunctions.KeyType;

import java.util.EnumMap;

public class SchnorrKeyGenerator implements SecretCreator<SchnorrSecret> {
    private static SchnorrKeyGenerator instance;

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
    public byte[] salt() {
        return "SchnorrKey".getBytes();
    }
}
