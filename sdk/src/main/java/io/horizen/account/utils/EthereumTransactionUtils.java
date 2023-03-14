package io.horizen.account.utils;

import io.horizen.account.proposition.AddressProposition;
import io.horizen.utils.BytesUtils;
import org.web3j.utils.Numeric;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;
import static io.horizen.account.utils.Secp256k1.LOWER_REAL_V;

public final class EthereumTransactionUtils {

    private EthereumTransactionUtils() {
        // prevent instantiation
    }

    // return minimal byte array representation of a long
    public static byte[] convertToBytes(long x) {
        BigInteger v = BigInteger.valueOf(x);
        //return v.toByteArray();
        return trimLeadingZeroFromByteArray(v);
    }

    // w3j way for converting bytes, it works also with generic byte contents (not only Long.BYTES byte arrays)
    public static long convertToLong(byte[] bytes) {
        BigInteger bi = Numeric.toBigInt(bytes);
        return bi.longValueExact();
    }

    public static long convertToLong(BigInteger val) {
        return val.longValueExact();
    }

    public static byte[] getRealV(byte[] bv) {
        long v = convertToLong(bv);
        if (v == LOWER_REAL_V || v == (LOWER_REAL_V + 1)) {
            return new byte[]{(byte) v};
        }
        int inc = 0;
        if ((int) v % 2 == 0) {
            inc = 1;
        }
        return new byte[]{(byte) (LOWER_REAL_V + inc)};
    }

    public static BigInteger getRealV(BigInteger bv) {
        long v = bv.longValue();
        if (v == LOWER_REAL_V || v == (LOWER_REAL_V + 1)) {
            return bv;
        }
        int inc = 0;
        if ((int) v % 2 == 0) {
            inc = 1;
        }
        return BigInteger.valueOf(LOWER_REAL_V + inc);
    }

    public static Optional<AddressProposition> getToAddressFromString(String toString) {
        if (toString == null) {
            return Optional.empty();
        } else {
            String toClean = Numeric.cleanHexPrefix(toString);
            if (toClean.isEmpty()) {
                return Optional.empty();
            } else {
                // sanity check of formatted string.
                //  Numeric library does not check hex characters' validity, BytesUtils does it
                var toBytes = BytesUtils.fromHexString(toClean);
                if (toBytes.length == 0) {
                    throw new IllegalArgumentException("Invalid input to string: " + toString);
                } else {
                    return Optional.of(new AddressProposition(toBytes));
                }
            }
        }
    }

    public static Optional<AddressProposition> getToAddressFromBytes(byte[] addressBytes) {
        if (addressBytes == null) {
            return Optional.empty();
        } else {
            if (addressBytes.length == 0) {
                return Optional.empty();
            } else {
                return Optional.of(new AddressProposition(addressBytes));
            }
        }
    }


    public static byte[] getDataFromString(String dataString) {
        if (dataString == null) {
            return new byte[]{};
        } else {
            String dataStringClean = Numeric.cleanHexPrefix(dataString);
            if (dataStringClean.isEmpty()) {
                return new byte[]{};
            } else {
                // sanity check of formatted string.
                //  Numeric library does not check hex characters' validity, BytesUtils does it
                var dataBytes = BytesUtils.fromHexString(dataStringClean);
                if (dataBytes.length == 0) {
                    throw new IllegalArgumentException("Invalid input to string: " + dataString);
                } else {
                    return dataBytes;
                }
            }
        }
    }

    public static byte[] trimLeadingZeroFromByteArray(BigInteger bi) {
        // toByteArray() returns all the data from both parts, which is the number of bits required for the
        // unsigned integer, and one bit for the sign.
        var barr = bi.toByteArray();
        if(barr[0] == 0x00) {
            return Arrays.copyOfRange(barr, 1, barr.length);
        } else {
            return barr;
        }
    }
}
