package io.horizen.account.utils;

import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;
import sparkz.util.serialization.Writer;

import java.util.Arrays;
import java.util.List;

import static io.horizen.account.utils.RlpStreamDecoder.OFFSET_SHORT_LIST;
import static io.horizen.account.utils.RlpStreamDecoder.OFFSET_SHORT_STRING;


/**
 * Adapted from org/web3j/rlp/RlpEncoder.java
 */
public class RlpStreamEncoder {

    public static void encode(RlpType value, Writer writer) {
        if (value instanceof RlpString) {
            encodeString((RlpString) value, writer);
        } else {
            encodeList((RlpList) value, writer);
        }
    }

    static void encodeString(RlpString value, Writer writer) {
        byte[] bytesValue = value.getBytes();

        if (bytesValue.length == 1 && bytesValue[0] >= (byte) 0x00) {
            writer.putBytes(bytesValue);
        } else if (bytesValue.length <= 55) {
            writer.putUByte(OFFSET_SHORT_STRING + bytesValue.length);
            writer.putBytes(bytesValue);
        } else {
            byte[] encodedStringLength = toMinimalByteArray(bytesValue.length);
            writer.putUByte((OFFSET_SHORT_STRING + 0x37) + encodedStringLength.length);
            writer.putBytes(encodedStringLength);
            writer.putBytes(bytesValue);
        }
    }

    static void encodeList(RlpList value, Writer writer) {
        List<RlpType> values = value.getValues();
        if (values.isEmpty()) {
            writer.putUByte(OFFSET_SHORT_LIST);
        } else {
            Writer helperWriter = writer.newWriter();
            for (RlpType entry : values) {
                encode(entry, helperWriter);
            }
            encodeNestedList(helperWriter, writer);
        }
    }

    private static void encodeNestedList(Writer inWriter, Writer outWriter) {
        int bytesLength = inWriter.length();
        if (bytesLength <= 55) {
            outWriter.putUByte(OFFSET_SHORT_LIST + bytesLength);
            outWriter.append(inWriter);
        } else {
            byte[] encodedStringLength = toMinimalByteArray(bytesLength);
            outWriter.putUByte((OFFSET_SHORT_LIST + 0x37) + encodedStringLength.length);
            outWriter.putBytes(encodedStringLength);
            outWriter.append(inWriter);
        }
    }

    private static byte[] toMinimalByteArray(int value) {
        byte[] encoded = toByteArray(value);

        for (int i = 0; i < encoded.length; i++) {
            if (encoded[i] != 0) {
                return Arrays.copyOfRange(encoded, i, encoded.length);
            }
        }

        return new byte[] {};
    }

    private static byte[] toByteArray(int value) {
        return new byte[] {
                (byte) ((value >> 24) & 0xff),
                (byte) ((value >> 16) & 0xff),
                (byte) ((value >> 8) & 0xff),
                (byte) (value & 0xff)
        };
    }
}
