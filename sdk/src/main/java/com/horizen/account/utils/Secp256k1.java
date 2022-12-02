package com.horizen.account.utils;

import com.google.common.primitives.Bytes;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Pair;
import org.glassfish.grizzly.http.util.HexUtils;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.CryptoUtils;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

import static org.web3j.crypto.Hash.sha256;
import static org.web3j.crypto.Sign.CHAIN_ID_INC;

public final class Secp256k1 {
    private static final int COORD_SIZE = 32;
    public static final int PRIVATE_KEY_SIZE = COORD_SIZE;
    public static final int PUBLIC_KEY_SIZE = COORD_SIZE * 2;

    // signature's v might be set to {0,1} + CHAIN_ID * 2 + 35 (see EIP155)
    public static final int SIGNATURE_V_MAXSIZE = Long.BYTES;
    public static final int SIGNATURE_RS_SIZE = 32;

    private Secp256k1() {
        // prevent instantiation
    }

    public static Pair<byte[], byte[]> createKeyPair(byte[] seed) {
        ECKeyPair keyPair = ECKeyPair.create(sha256(seed));

        return new Pair<>(keyPair.getPrivateKey().toByteArray(), keyPair.getPublicKey().toByteArray());
    }

    public static boolean verify(byte[] signature, byte[] message, byte[] publicKey) {
        BigInteger publicKeyNumeric = Numeric.toBigInt(publicKey);
        ECDSASignature ecdsaSignature = CryptoUtils.fromDerFormat(signature);
        Sign.SignatureData signatureData = Sign.createSignatureData(ecdsaSignature, publicKeyNumeric, message);

        int chainId = (BytesUtils.getInt(signatureData.getV(), 0) - CHAIN_ID_INC) / 2;
        int recId = Sign.getRecId(signatureData, chainId);

        BigInteger recoveredPublicKey = Sign.recoverFromSignature(recId, ecdsaSignature, message);

        return recoveredPublicKey.equals(publicKeyNumeric);
    }

    public static byte[] sign(byte[] privateKey, byte[] message) {
        ECKeyPair keyPair = ECKeyPair.create(privateKey);
        Sign.SignatureData signatureData = Sign.signMessage(message, keyPair);
        byte[] signature = Bytes.concat(signatureData.getR(),signatureData.getS(),signatureData.getV());

        return signature;
    }
}
