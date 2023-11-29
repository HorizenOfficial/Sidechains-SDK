package io.horizen.utils;

import org.apache.logging.log4j.LogManager;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import sparkz.crypto.hash.Blake2b256;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Random;
import java.util.logging.Logger;

public final class Utils
{
    static {
        // for Ripemd160 hash
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Utils() {}

    public static final int SHA256_LENGTH = 32;

    public static final byte[] ZEROS_HASH = new byte[SHA256_LENGTH];

    public static byte[] doubleSHA256Hash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes, 0, bytes.length);
            byte[] first = digest.digest();
            return digest.digest(first);
        } catch (NoSuchAlgorithmException e) {
            LogManager.getLogger().error("Unexpected exception: ", e);
            throw new RuntimeException(e);  // Cannot happen.
        }
    }

    public static byte[] Ripemd160Sha256Hash(byte[] bytes) {
        try {
            MessageDigest digest1 = MessageDigest.getInstance("SHA-256");
            MessageDigest digest2 = MessageDigest.getInstance("RIPEMD160");

            digest1.update(bytes, 0, bytes.length);
            byte[] first = digest1.digest();

            digest2.update(first, 0, first.length);
            return digest2.digest();
        } catch (NoSuchAlgorithmException e) {
            LogManager.getLogger().error("Unexpected exception: ", e);
            throw new RuntimeException(e);  // Cannot happen.
        }
    }

    public static byte[] Ripemd160Hash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("RIPEMD160");
            digest.update(bytes, 0, bytes.length);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            LogManager.getLogger().error("Unexpected exception: ", e);
            throw new RuntimeException(e);  // Cannot happen.
        }
    }

    public static byte[] doubleSHA256HashOfConcatenation(byte[] bytes1, byte[] bytes2) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes1, 0, bytes1.length);
            digest.update(bytes2, 0, bytes2.length);
            byte[] first = digest.digest();
            return digest.digest(first);
        } catch (NoSuchAlgorithmException e) {
            LogManager.getLogger().error("Unexpected exception: ", e);
            throw new RuntimeException(e);  // Cannot happen.
        }
    }

    /** source Bitcoinj
     * <p>The "compact" format is a representation of a whole number N using an unsigned 32 bit number similar to a
     * floating point format. The most significant 8 bits are the unsigned exponent of base 256. This exponent can
     * be thought of as "number of bytes of N". The lower 23 bits are the mantissa. Bit number 24 (0x800000) represents
     * the sign of N. Therefore, N = (-1^sign) * mantissa * 256^(exponent-3).</p>
     *
     * <p>Satoshi's original implementation used BN_bn2mpi() and BN_mpi2bn(). MPI uses the most significant bit of the
     * first byte as sign. Thus 0x1234560000 is compact 0x05123456 and 0xc0de000000 is compact 0x0600c0de. Compact
     * 0x05c0de00 would be -0x40de000000.</p>
     *
     * <p>Bitcoin only uses this "compact" format for encoding difficulty targets, which are unsigned 256bit quantities.
     * Thus, all the complexities of the sign bit and using base 256 are probably an implementation accident.</p>
     */
    public static BigInteger decodeCompactBits(long compact) {
        int size = ((int) (compact >> 24)) & 0xFF;
        byte[] bytes = new byte[4 + size];
        bytes[3] = (byte) size;
        if (size >= 1) bytes[4] = (byte) ((compact >> 16) & 0xFF);
        if (size >= 2) bytes[5] = (byte) ((compact >> 8) & 0xFF);
        if (size >= 3) bytes[6] = (byte) (compact & 0xFF);
        return decodeMPI(bytes, true);
    }

    public static long encodeCompactBits(BigInteger value) {
        long result;
        int size = value.toByteArray().length;
        if (size <= 3)
            result = value.longValueExact() << 8 * (3 - size);
        else
            result = value.shiftRight(8 * (size - 3)).longValueExact();
        // The 0x00800000 bit denotes the sign.
        // Thus, if it is already set, divide the mantissa by 256 and increase the exponent.
        if ((result & 0x00800000L) != 0) {
            result >>= 8;
            size++;
        }
        result |= size << 24;
        result |= value.signum() == -1 ? 0x00800000 : 0;
        return result;
    }

    /**
     * MPI encoded numbers are produced by the OpenSSL BN_bn2mpi function. They consist of
     * a 4 byte big endian length field, followed by the stated number of bytes representing
     * the number in big endian format (with a sign bit).
     * @param hasLength can be set to false if the given array is missing the 4 byte length field
     */
    public static BigInteger decodeMPI(byte[] mpi, boolean hasLength) {
        byte[] buf;
        if (hasLength) {
            int length = (int) readUint32BE(mpi, 0);
            buf = new byte[length];
            System.arraycopy(mpi, 4, buf, 0, length);
        } else
            buf = mpi;
        if (buf.length == 0)
            return BigInteger.ZERO;
        boolean isNegative = (buf[0] & 0x80) == 0x80;
        if (isNegative)
            buf[0] &= 0x7f;
        BigInteger result = new BigInteger(buf);
        return isNegative ? result.negate() : result;
    }

    /** Parse 4 bytes from the byte array (starting at the offset) as unsigned 32-bit integer in big endian format. */
    public static long readUint32BE(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xffl) << 24) |
                ((bytes[offset + 1] & 0xffl) << 16) |
                ((bytes[offset + 2] & 0xffl) << 8) |
                (bytes[offset + 3] & 0xffl);
    }

    public static byte[] nextVersion() {
        byte[] version = new byte[32];
        Random r = new Random();
        r.nextBytes(version);
        return version;
    }

    public static ByteArrayWrapper calculateKey(byte[] data) {
        return new ByteArrayWrapper((byte[]) Blake2b256.hash(data));
    }

}
