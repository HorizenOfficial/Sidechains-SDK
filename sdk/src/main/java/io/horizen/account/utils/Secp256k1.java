package io.horizen.account.utils;

import io.horizen.account.proposition.AddressProposition;
import io.horizen.evm.Address;
import io.horizen.utils.ChaChaPrngSecureRandom;
import io.horizen.utils.Pair;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;
import sparkz.crypto.hash.Keccak256;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.Arrays;
import java.security.*;

import static io.horizen.account.utils.BigIntegerUInt256.getUnsignedByteArray;
import static io.horizen.utils.BytesUtils.padWithZeroBytes;

public final class Secp256k1 {
    private static final int COORD_SIZE = 32;
    public static final int PRIVATE_KEY_SIZE = COORD_SIZE;
    public static final int PUBLIC_KEY_SIZE = COORD_SIZE * 2;

    public static final int SIGNATURE_RS_SIZE = 32;

    public static final int CHAIN_ID_INC = Sign.CHAIN_ID_INC;

    public static final int LOWER_REAL_V = Sign.LOWER_REAL_V;

    public static final int REAL_V_REPLAY_PROTECTED = 35;

    public static class Signature {
        public final BigInteger v;
        public final BigInteger r;
        public final BigInteger s;

        public Signature(byte[] v, byte[] r, byte[] s) {
            this.v = new BigInteger(1, v);
            this.r = new BigInteger(1, r);
            this.s = new BigInteger(1, s);
        }
    }

    private Secp256k1() {
        // prevent instantiation
    }

    public static Pair<byte[], byte[]> createKeyPair(byte[] seed) {
        try {
            SecureRandom rnd = ChaChaPrngSecureRandom.getInstance(seed);
            ECKeyPair keyPair = Keys.createEcKeyPair(rnd);

            byte[] privateKey = Numeric.toBytesPadded(keyPair.getPrivateKey(), PRIVATE_KEY_SIZE);
            byte[] publicKey = Numeric.toBytesPadded(keyPair.getPublicKey(), PUBLIC_KEY_SIZE);
            return new Pair<>(privateKey, publicKey);
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException e) {
            // Should never happen
            return null;
        }
    }

    public static byte[] signedMessageToAddress(byte[] message, BigInteger v, BigInteger r, BigInteger s) throws SignatureException {
        byte[] v_barr = getUnsignedByteArray(v);

        // w3j wants only 32 bytes long array, pad with 0x00 if necessary
        byte[] r_barr = padWithZeroBytes(getUnsignedByteArray(r), SIGNATURE_RS_SIZE);
        byte[] s_barr = padWithZeroBytes(getUnsignedByteArray(s), SIGNATURE_RS_SIZE);

        Sign.SignatureData signatureData = new Sign.SignatureData(v_barr, r_barr, s_barr);
        BigInteger pubKey = Sign.signedMessageToKey(message, signatureData);
        return getAddress(Numeric.toBytesPadded(pubKey, PUBLIC_KEY_SIZE));
    }

    public static boolean verify(byte[] message, BigInteger v, BigInteger r, BigInteger s, byte[] address) {
        try {
            byte[] signingAddress = signedMessageToAddress(message, v, r, s);
            return Arrays.equals(signingAddress, address);
        } catch (SignatureException e) {
            // invalid signature or could not recover public key from signature
            return false;
        }
    }

    public static Signature sign(byte[] privateKey, byte[] message) {
        ECKeyPair keyPair = ECKeyPair.create(privateKey);
        Sign.SignatureData signatureData = Sign.signMessage(message, keyPair, true);

        return new Signature(signatureData.getV(), signatureData.getR(), signatureData.getS());
    }

    public static byte[] getPublicKey(byte[] privateKey) {
        return Numeric.toBytesPadded(ECKeyPair.create(privateKey).getPublicKey(), Secp256k1.PUBLIC_KEY_SIZE);
    }

    public static byte[] getAddress(byte[] publicKey) {
        // Address is the last Account.ADDRESS_SIZE bytes of public key Keccak256 hash
        byte[] hashedKey = (byte[]) Keccak256.hash(publicKey);

        return Arrays.copyOfRange(hashedKey, hashedKey.length - AddressProposition.LENGTH, hashedKey.length);
    }

    public static String checksumAddress(byte[] address) {
        return Keys.toChecksumAddress(Numeric.toHexString(address));
    }

    public static Address generateContractAddress(Address from, BigInteger nonce) {
        return new Address(ContractUtils.generateContractAddress(from.toBytes(), nonce));
    }
}
