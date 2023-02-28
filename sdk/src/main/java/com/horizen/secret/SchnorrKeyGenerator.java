package com.horizen.secret;

import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.cryptolibprovider.SchnorrFunctions;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;

public class SchnorrKeyGenerator implements SecretCreator<SchnorrSecret> {
    private static final SchnorrKeyGenerator instance;
    private static final byte[] domain = "SchnorrKey".getBytes(StandardCharsets.UTF_8);

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

    /**
     * Method to get salt.
     * In this case salt serves as a domain separation
     *
     * @return salt as byte array in UTF-8 encoding
     */
    @Override
    public byte[] salt() {
        return domain;
    }
}
