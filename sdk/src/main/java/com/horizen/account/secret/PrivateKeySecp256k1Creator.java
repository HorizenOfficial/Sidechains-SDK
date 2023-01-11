package com.horizen.account.secret;

import com.horizen.account.utils.Secp256k1;
import com.horizen.secret.SecretCreator;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;

import java.security.SecureRandom;
import java.util.Arrays;

public final class PrivateKeySecp256k1Creator implements SecretCreator<PrivateKeySecp256k1>
{
    private static PrivateKeySecp256k1Creator instance;

    private final String domain = "PrivateKeySecp25519k1";

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
        try {
            ECKeyPair keyPair = Keys.createEcKeyPair(new SecureRandom(seed));
            // keyPair private key can be 32 or 33 bytes long
            byte[] privateKey = Arrays.copyOf(keyPair.getPrivateKey().toByteArray(), Secp256k1.PRIVATE_KEY_SIZE);
            return new PrivateKeySecp256k1(privateKey);
        } catch (Exception e) {
            // TODO handle it
            System.out.println("Exception: "+ e.getMessage());
            return null;
        }
    }

    @Override
    public byte[] salt() {
        return domain.getBytes();
    }
}
