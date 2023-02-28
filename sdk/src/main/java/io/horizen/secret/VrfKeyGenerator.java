package io.horizen.secret;

import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.cryptolibprovider.VrfFunctions.KeyType;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;

public class VrfKeyGenerator implements SecretCreator<VrfSecretKey> {
    private static final VrfKeyGenerator instance;
    private static final byte[] domain = "VrfKey".getBytes(StandardCharsets.UTF_8);

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
