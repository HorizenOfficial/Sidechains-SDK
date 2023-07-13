package io.horizen.utils;

import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.common.primitives.Shorts;
import io.horizen.params.MainNetParams;
import io.horizen.params.NetworkParams;
import sparkz.util.encode.Base58;

import java.util.Arrays;
import java.util.Collection;

public final class BytesUtils {
    private BytesUtils() {}

    // Get Short value from byte array starting from an offset position without copying an array
    public static short getShort(byte[] bytes, int offset) {
        if(offset < 0 || bytes.length < offset + 2)
            throw new IllegalArgumentException("Value is out of array bounds");

        return Shorts.fromBytes(bytes[offset],
                                bytes[offset + 1]);
    }

    // Get Reversed Short value from byte array starting from an offset position without copying an array
    public static short getReversedShort(byte[] bytes, int offset) {
        if(offset < 0 || bytes.length < offset + 2)
            throw new IllegalArgumentException("Value is out of array bounds");

        return Shorts.fromBytes(bytes[offset + 1],
                                bytes[offset]);
    }

    // Get Int value from byte array starting from an offset position without copying an array
    public static int getInt(byte[] bytes, int offset) {
        if(offset < 0 || bytes.length < offset + 4)
            throw new IllegalArgumentException("Value is out of array bounds");

        return Ints.fromBytes(  bytes[offset],
                                bytes[offset + 1],
                                bytes[offset + 2],
                                bytes[offset + 3]);
    }

    // Get Reversed Int value from byte array starting from an offset position without copying an array
    public static int getReversedInt(byte[] bytes, int offset) {
        if(offset < 0 || bytes.length < offset + 4)
            throw new IllegalArgumentException("Value is out of array bounds");

        return Ints.fromBytes(  bytes[offset + 3],
                                bytes[offset + 2],
                                bytes[offset + 1],
                                bytes[offset]);
    }

    // Get Long value from byte array starting from an offset position without copying an array
    public static long getLong(byte[] bytes, int offset) {
        if(offset < 0 || bytes.length < offset + 8)
            throw new IllegalArgumentException("Value is out of array bounds");

        return Longs.fromBytes( bytes[offset],
                                bytes[offset + 1],
                                bytes[offset + 2],
                                bytes[offset + 3],
                                bytes[offset + 4],
                                bytes[offset + 5],
                                bytes[offset + 6],
                                bytes[offset + 7]);
    }

    // Get Reversed Long value from byte array starting from an offset position without copying an array
    public static long getReversedLong(byte[] bytes, int offset) {
        if(offset < 0 || bytes.length < offset + 8)
            throw new IllegalArgumentException("Value is out of array bounds");

        return Longs.fromBytes( bytes[offset + 7],
                                bytes[offset + 6],
                                bytes[offset + 5],
                                bytes[offset + 4],
                                bytes[offset + 3],
                                bytes[offset + 2],
                                bytes[offset + 1],
                                bytes[offset]);
    }

    // Bitcoin `ReadCompactSize` method return value which length is from 1 to 9 bytes, starting from an offset position without copying an array.
    // Note: original "value" in bitcoin is stored in little endian (reversed).
    // Used in std::vectors serialization to store the length of the vector.
    public static CompactSize getCompactSize(byte[] bytes, int offset) {
        if(offset < 0 || bytes.length < offset + 1)
            throw new IllegalArgumentException("CompactSize: Value is out of array bounds");

        byte first = bytes[offset];
        int size;
        long value;
        switch(first) {
            case (byte)253:
                size = 3;
                value = BytesUtils.getReversedShort(bytes, offset + 1) & 0xFFFF;
                if(value < 253)
                    throw new IllegalArgumentException("CompactSize: non-canonical value");

                break;

            case (byte)254:
                size = 5;
                value = BytesUtils.getReversedInt(bytes, offset + 1) & 0xFFFFFFFFL;
                if(value < 0x10000L)
                    throw new IllegalArgumentException("CompactSize: non-canonical value");
                break;

            case (byte)255:
                size = 9;
                value = BytesUtils.getReversedLong(bytes, offset + 1);
                if(value < 0x100000000L)
                    throw new IllegalArgumentException("CompactSize: non-canonical value");
                break;

            default:
                size = 1;
                value = first & 0xFF;
        }
        if(value > CompactSize.MAX_SERIALIZED_COMPACT_SIZE)
            throw new IllegalArgumentException("CompactSize: size too large");

        return new CompactSize(value, size);
    }

    // Get byte array representation of CompactSize value
    // Note: we write the data in LE as MC does.
    public static byte[] toCompactSizeBytes(CompactSize vi) {
        byte[] res = new byte[vi.size()];
        switch (vi.size()) {
            case 1:
                res[0] = (byte) (vi.value() & 255L);
                break;

            case 3:
                res[0] = (byte)253;
                res[1] = (byte) (vi.value() & 255L);
                res[2] = (byte) ((vi.value() >> 8) & 255L);
                break;

            case 5:
                res[0] = (byte)254;
                res[1] = (byte) (vi.value() & 255L);
                res[2] = (byte) ((vi.value() >> 8) & 255L);
                res[3] = (byte) ((vi.value() >> 16) & 255L);
                res[4] = (byte) ((vi.value() >> 24) & 255L);
                break;

            case 9:
                res[0] = (byte)255;
                res[1] = (byte) (vi.value() & 255L);
                res[2] = (byte) ((vi.value() >> 8) & 255L);
                res[3] = (byte) ((vi.value() >> 16) & 255L);
                res[4] = (byte) ((vi.value() >> 24) & 255L);
                res[5] = (byte) ((vi.value() >> 32) & 255L);
                res[6] = (byte) ((vi.value() >> 40) & 255L);
                res[7] = (byte) ((vi.value() >> 48) & 255L);
                res[8] = (byte) ((vi.value() >> 56) & 255L);
                break;

            default: throw new IllegalArgumentException("Incorrect size of CompactSize had been detected:" + vi.size());
        }
        return res;
    }

    // Get reversed copy of byte array
    public static byte[] reverseBytes(byte[] bytes) {
        byte[] res = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++)
            res[i] = bytes[bytes.length - 1 - i];
        return res;
    }

    public static byte[] toMainchainFormat(byte[] bytes) {
        return reverseBytes(bytes);
    }

    // Get byte array from hex string;
    public static byte[] fromHexString(String hex) {
        return BaseEncoding.base16().lowerCase().decode(hex.toLowerCase());
    }

    // Get hex string representation of byte array
    public static String toHexString(byte[] bytes) {
        return BaseEncoding.base16().lowerCase().encode(bytes);
    }

    public static boolean contains(Collection<byte[]> collection, byte[] value) {
        for (byte [] v : collection) {
            if (Arrays.equals(v, value))
                return true;
        }
        return false;
    }

    private static final int HORIZEN_PUBLIC_KEY_ADDRESS_PREFIX_LENGTH = 2;
    private static final int HORIZEN_PUBLIC_KEY_ADDRESS_HASH_LENGTH = 20;
    private static final int HORIZEN_PUBLIC_KEY_ADDRESS_CHECKSUM_LENGTH = 4;

    private static final int HORIZEN_PUBLIC_KEY_ADDRESS_BASE_58_LENGTH = 35;

    private static final byte[] PUBLIC_KEY_MAINNET_PREFIX = BytesUtils.fromHexString("2089"); // "zn"
    private static final byte[] PUBLIC_KEY_MAINNET_PREFIX_OLD = BytesUtils.fromHexString("1CB8"); // "t1"

    private static final byte[] PUBLIC_KEY_TESTNET_PREFIX = BytesUtils.fromHexString("2098"); // "zt"
    private static final byte[] PUBLIC_KEY_TESTNET_PREFIX_OLD = BytesUtils.fromHexString("1D25"); // "tm"

    public static byte[] fromHorizenPublicKeyAddress(String address, NetworkParams params) {
        if(address.length() != HORIZEN_PUBLIC_KEY_ADDRESS_BASE_58_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect Horizen public key address length %d", address.length()));

        byte[] addressBytesWithChecksum = Base58.decode(address).get();
        byte[] addressBytes = Arrays.copyOfRange(addressBytesWithChecksum, 0, HORIZEN_PUBLIC_KEY_ADDRESS_PREFIX_LENGTH + HORIZEN_PUBLIC_KEY_ADDRESS_HASH_LENGTH);


        // Check version
        byte[] prefix = Arrays.copyOfRange(addressBytes, 0, HORIZEN_PUBLIC_KEY_ADDRESS_PREFIX_LENGTH);
        if(params instanceof MainNetParams) {
            if(!Arrays.equals(prefix, PUBLIC_KEY_MAINNET_PREFIX) && !Arrays.equals(prefix, PUBLIC_KEY_MAINNET_PREFIX_OLD))
                throw new IllegalArgumentException("Incorrect Horizen public key address format, MainNet format expected.");
        }
        else if(!Arrays.equals(prefix, PUBLIC_KEY_TESTNET_PREFIX) && !Arrays.equals(prefix, PUBLIC_KEY_TESTNET_PREFIX_OLD))
            throw new IllegalArgumentException("Incorrect Horizen public key address format, TestNet format expected.");

        byte[] publicKeyHash = Arrays.copyOfRange(addressBytes, HORIZEN_PUBLIC_KEY_ADDRESS_PREFIX_LENGTH, HORIZEN_PUBLIC_KEY_ADDRESS_PREFIX_LENGTH + HORIZEN_PUBLIC_KEY_ADDRESS_HASH_LENGTH);

        // Verify checksum
        byte[] checksum = Arrays.copyOfRange(addressBytesWithChecksum, HORIZEN_PUBLIC_KEY_ADDRESS_PREFIX_LENGTH + HORIZEN_PUBLIC_KEY_ADDRESS_HASH_LENGTH, addressBytesWithChecksum.length);
        byte[] calculatedChecksum = Arrays.copyOfRange(Utils.doubleSHA256Hash(addressBytes), 0, HORIZEN_PUBLIC_KEY_ADDRESS_CHECKSUM_LENGTH);
        if(!Arrays.equals(calculatedChecksum, checksum))
            throw new IllegalArgumentException("Broken Horizen public key address: checksum is wrong.");

        return publicKeyHash;
    }

    public static String toHorizenPublicKeyAddress(byte[] publicKeyHashBytes, NetworkParams params) {
        if(publicKeyHashBytes.length != HORIZEN_PUBLIC_KEY_ADDRESS_HASH_LENGTH)
            throw new IllegalArgumentException("Incorrect Horizen public key address bytes length.");

        byte[] prefix = params instanceof MainNetParams ? PUBLIC_KEY_MAINNET_PREFIX : PUBLIC_KEY_TESTNET_PREFIX;
        byte[] addressBytes = Bytes.concat(prefix, publicKeyHashBytes);
        byte[] checksum = Arrays.copyOfRange(Utils.doubleSHA256Hash(addressBytes), 0, HORIZEN_PUBLIC_KEY_ADDRESS_CHECKSUM_LENGTH);
        return Base58.encode(Bytes.concat(addressBytes, checksum));
    }

    // Get size in bytes needed to fit the number of bits
    public static int getBytesFromBits(int nbits) {
        if(nbits < 0)
            return 0;

        int reminder = nbits % 8;
        int bytes = nbits / 8;
        if(reminder > 0)
            bytes++;

        return bytes;
    }

    // pad an array prepending the necessary 0x00 bytes up to the wanted size
    public static byte[] padWithZeroBytes(byte[] src, int destSize) {
        if (src != null && src.length < destSize) {
            byte[] padded_s = new byte[destSize];
            System.arraycopy(src, 0, padded_s, destSize - src.length, src.length);
            return padded_s;
        }
        return src;
    }

}
