package com.horizen.secret;

import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.cryptolibprovider.utils.SchnorrFunctions;

import java.util.EnumMap;

public class SchnorrKeyGenerator implements SecretCreator<SchnorrSecret> {
    private static final SchnorrKeyGenerator instance;

    private final String domain = "SchnorrKey";

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
        EnumMap<SchnorrFunctions.KeyType, byte[]> keys = CryptoLibProvider.schnorrFunctions().generateSchnorrKeys(seed);
        return new SchnorrSecret(keys.get(SchnorrFunctions.KeyType.SECRET), keys.get(SchnorrFunctions.KeyType.PUBLIC));
    }

    @Override
    public byte[] salt() {
        return domain.getBytes();
    }
}
