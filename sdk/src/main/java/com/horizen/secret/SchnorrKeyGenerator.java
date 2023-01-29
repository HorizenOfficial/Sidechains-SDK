package com.horizen.secret;

import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.cryptolibprovider.utils.SchnorrFunctions;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;

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
        EnumMap<SchnorrFunctions.KeyType, byte[]> keys = CryptoLibProvider.schnorrFunctions().generateSchnorrKeys(seed);
        return new SchnorrSecret(keys.get(SchnorrFunctions.KeyType.SECRET), keys.get(SchnorrFunctions.KeyType.PUBLIC));
    }

    @Override
    public byte[] salt() {
        String domain = "SchnorrKey";
        return domain.getBytes(StandardCharsets.UTF_8);
    }
}
