package com.horizen.account.utils;

import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Pair;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;

import static org.web3j.crypto.TransactionEncoder.createEip155SignatureData;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Objects;

public final class Secp256k1 {
    private static final int COORD_SIZE = 32;
    public static final int PRIVATE_KEY_SIZE = COORD_SIZE;
    public static final int PUBLIC_KEY_SIZE = COORD_SIZE * 2;

    // signature's v might be set to {0,1} + CHAIN_ID * 2 + 35 (see EIP155)
    public static final int SIGNATURE_V_MAXSIZE = Long.BYTES;
    public static final int SIGNATURE_RS_SIZE = 32;

    public static final int ADDRESS_LENGTH_IN_HEX = 40;

    public static final int CHAIN_ID_INC = Sign.CHAIN_ID_INC;

    public static final int LOWER_REAL_V = Sign.LOWER_REAL_V;

    private Secp256k1() {
        // prevent instantiation
    }

    public static Pair<byte[], byte[]> createKeyPair(byte[] seed) {
        // Check why is this failing
        try {
            ECKeyPair keyPair = Keys.createEcKeyPair(new SecureRandom(seed));
            return new Pair<>(keyPair.getPrivateKey().toByteArray(), keyPair.getPublicKey().toByteArray());
        } catch (Exception e) {
            // TODO handle it
            System.out.println("Exception: " + e.getMessage());
            return null;
        }
    }

    public static boolean verify(byte[] v, byte[] r, byte[] s, byte[] message, byte[] address) {
        try {
            var signature = new Sign.SignatureData(v, r, s);
            var signingAddress = Keys.getAddress(Sign.signedMessageToKey(message, signature));

            return Objects.equals(signingAddress, BytesUtils.toHexString(address));
        } catch (SignatureException e) {
            return false;
        }
    }

    public static SignatureSecp256k1 sign(byte[] privateKey, byte[] message) {
        ECKeyPair keyPair = ECKeyPair.create(privateKey);
        var signature = Sign.signMessage(message, keyPair, true);

        return new SignatureSecp256k1(signature.getV(), signature.getR(), signature.getS());
    }

    public static Sign.SignatureData signMessage(byte[] message, Pair<byte[], byte[]> keyPair, boolean needToHash) {
        ECKeyPair ecKeyPair = ECKeyPair.create(keyPair.getKey());
        return Sign.signMessage(message, ecKeyPair, needToHash);
    }

    public static String checksumAddress(byte[] address) {
        return Keys.toChecksumAddress(BytesUtils.toHexString(address));
    }

    public static byte[] getPublicKey(byte[] privateKey) {
        return Numeric.toBytesPadded(ECKeyPair.create(privateKey).getPublicKey(), Secp256k1.PUBLIC_KEY_SIZE);
    }

    public static byte[] sha3(byte[] input) {
        return Hash.sha3(input);
    }

    public static byte[] sha3(byte[] input, int offset, int length) {
        return Hash.sha3(input, offset, length);
    }

    public static BigInteger signedMessageToKey(byte[] message, Sign.SignatureData signatureData) throws SignatureException {
        return Sign.signedMessageToKey(message, signatureData);
    }

    public static AddressProposition getAddressFromPublicKey(BigInteger publicKey) {
        return new AddressProposition(Keys.getAddress(Numeric.toBytesPadded(publicKey, PUBLIC_KEY_SIZE)));
    }

    public static Sign.SignatureData getSignatureData(byte[] v, byte[] r, byte[] s) {
        return new Sign.SignatureData(v, r, s);
    }

    public static byte[] generateContractAddress(byte[] address, BigInteger nonce) {
        return ContractUtils.generateContractAddress(address, nonce);
    }

    public static byte[] getVFromRecId(int recId) {
        return Sign.getVFromRecId(recId);
    }

    public static Sign.SignatureData createEip155SignatureData(Sign.SignatureData signatureData, long chainId) {
        return TransactionEncoder.createEip155SignatureData(signatureData, chainId);
    }

    public static int getRecId(Sign.SignatureData signatureData, long chainId) {
        return Sign.getRecId(signatureData, chainId);
    }

    public static AddressProposition getAddress(byte[] publicKey) {
        var hashedKey = Secp256k1.sha3(publicKey);
        return new AddressProposition(Arrays.copyOfRange(hashedKey, hashedKey.length - Account.ADDRESS_SIZE, hashedKey.length));
    }

    public enum TransactionType {
        LEGACY((Byte) null),
        EIP1559((byte) 2);

        Byte type;

        private TransactionType(Byte type) {
            this.type = type;
        }

        public Byte getRlpType() {
            return this.type;
        }

        public boolean isLegacy() {
            return this.equals(LEGACY);
        }

        public boolean isEip1559() {
            return this.equals(EIP1559);
        }
    }
}
