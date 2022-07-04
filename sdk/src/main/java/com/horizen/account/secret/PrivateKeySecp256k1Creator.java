package com.horizen.account.secret;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.account.utils.Secp256k1;
import com.horizen.node.NodeWalletBase;
import com.horizen.secret.Secret;
import com.horizen.secret.SecretCreator;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import scorex.crypto.hash.Keccak256;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

public final class PrivateKeySecp256k1Creator implements SecretCreator<PrivateKeySecp256k1>
{
    private static PrivateKeySecp256k1Creator instance;

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
    public PrivateKeySecp256k1 generateNextSecret(NodeWalletBase wallet) {
        List<Secret> prevSecrets = wallet.secretsOfType(PrivateKeySecp256k1.class);
        byte[] nonce = Ints.toByteArray(prevSecrets.size());
        byte[] seed = Keccak256.hash(Bytes.concat(wallet.walletSeed(), nonce));

        return generateSecret(seed);
    }
}
