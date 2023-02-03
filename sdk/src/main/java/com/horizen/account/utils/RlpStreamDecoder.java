package com.horizen.account.utils;

import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import sparkz.util.serialization.Reader;
import java.util.ArrayList;

/**
 * Adapted from org/web3j/rlp/RlpDecoder.java
 */
public class RlpStreamDecoder {

    /**
     * [0x80] If a string is 0-55 bytes long, the RLP encoding consists of a single byte with value
     * 0x80 plus the length of the string followed by the string. The range of the first byte is
     * thus [0x80, 0xb7].
     */
    public static int OFFSET_SHORT_STRING = 0x80;

    /**
     * [0xb7] If a string is more than 55 bytes long, the RLP encoding consists of a single byte
     * with value 0xb7 plus the length of the length of the string in binary form, followed by the
     * length of the string, followed by the string. For example, a length-1024 string would be
     * encoded as \xb9\x04\x00 followed by the string. The range of the first byte is thus [0xb8,
     * 0xbf].
     */
    public static int OFFSET_LONG_STRING = 0xb7;

    /**
     * [0xc0] If the total payload of a list (i.e. the combined length of all its items) is 0-55
     * bytes long, the RLP encoding consists of a single byte with value 0xc0 plus the length of the
     * list followed by the concatenation of the RLP encodings of the items. The range of the first
     * byte is thus [0xc0, 0xf7].
     */
    public static int OFFSET_SHORT_LIST = 0xc0;

    /**
     * [0xf7] If the total payload of a list is more than 55 bytes long, the RLP encoding consists
     * of a single byte with value 0xf7 plus the length of the length of the list in binary form,
     * followed by the length of the list, followed by the concatenation of the RLP encodings of the
     * items. The range of the first byte is thus [0xf8, 0xff].
     */
    public static int OFFSET_LONG_LIST = 0xf7;

    /**
     * Parse stream reader into RLP elements.
     *
     * @param reader - RLP encoded byte stream
     * @return recursive RLP structure
     */
    public static RlpList decode(Reader reader) {
        RlpList rlpList = new RlpList(new ArrayList<>());
        if (reader != null) {
            traverse(reader, -1, rlpList);
        }
        return rlpList;
    }

    /**
     * Recursively parse and decode nested RLP data structures
     * @param reader - RLP encoded byte stream
     * @param levelBytesToRead - The number of bytes to read if this number is known, otherwise should be set to -1
     * @param rlpList - the resulting list to fill with decoded items
     */
    private static void traverse(Reader reader, int levelBytesToRead, RlpList rlpList) {

        try {
            if (reader == null || reader.remaining() == 0) {
                return;
            }
            int startLevelConsumedBytes = reader.consumed();

            while (reader.remaining() > 0) {

                int consumedBytes = reader.consumed();

                if (levelBytesToRead < 0) {
                    if (consumedBytes > startLevelConsumedBytes) {
                        // we completed the reading of the start level
                        return;
                    }
                } else {
                    if ((startLevelConsumedBytes + levelBytesToRead) == consumedBytes) {
                        // we completed the reading of an internal level
                        return;
                    }
                }

                int prefix = reader.getUByte();

                if (prefix < OFFSET_SHORT_STRING) {

                    // 1. the data is a string if the range of the
                    // first byte(i.e. prefix) is [0x00, 0x7f],
                    // and the string is the first byte itself exactly;

                    byte[] rlpData = {(byte) prefix};
                    rlpList.getValues().add(RlpString.create(rlpData));

                } else if (prefix == OFFSET_SHORT_STRING) {

                    // null
                    rlpList.getValues().add(RlpString.create(new byte[0]));

                } else if (prefix > OFFSET_SHORT_STRING && prefix <= OFFSET_LONG_STRING) {

                    // 2. the data is a string if the range of the
                    // first byte is [0x80, 0xb7], and the string
                    // which length is equal to the first byte minus 0x80
                    // follows the first byte;

                    byte strLen = (byte) (prefix - OFFSET_SHORT_STRING);

                    // Input validation
                    if (reader.remaining() < strLen) {
                        throw new RuntimeException(
                                "RLP length mismatch: remaining bytes in stream reader: " + reader.remaining() +
                                ", wanted: " + strLen);
                    }

                    byte[] rlpData = reader.getBytes(strLen);
                    rlpList.getValues().add(RlpString.create(rlpData));

                } else if (prefix > OFFSET_LONG_STRING && prefix < OFFSET_SHORT_LIST) {

                    // 3. the data is a string if the range of the
                    // first byte is [0xb8, 0xbf], and the length of the
                    // string which length in bytes is equal to the
                    // first byte minus 0xb7 follows the first byte,
                    // and the string follows the length of the string;

                    byte lenOfStrLen = (byte) (prefix - OFFSET_LONG_STRING);
                    int strLen = calcLength(lenOfStrLen, reader);

                    // Input validation
                    if (reader.remaining() < strLen) {
                        throw new RuntimeException(
                                "RLP length mismatch: remaining bytes in stream reader: " + reader.remaining() +
                                ", wanted: " + strLen);
                    }

                    // now we can parse an item for data[1]..data[length]
                    byte[] rlpData = reader.getBytes(strLen);

                    rlpList.getValues().add(RlpString.create(rlpData));

                } else if (prefix >= OFFSET_SHORT_LIST && prefix <= OFFSET_LONG_LIST) {

                    // 4. the data is a list if the range of the
                    // first byte is [0xc0, 0xf7], and the concatenation of
                    // the RLP encodings of all items of the list which the
                    // total payload is equal to the first byte minus 0xc0 follows the first byte;

                    byte listLen = (byte) (prefix - OFFSET_SHORT_LIST);

                    RlpList newLevelList = new RlpList(new ArrayList<>());
                    traverse(reader, listLen, newLevelList);
                    rlpList.getValues().add(newLevelList);

                } else if (prefix > OFFSET_LONG_LIST) {

                    // 5. the data is a list if the range of the
                    // first byte is [0xf8, 0xff], and the total payload of the
                    // list which length is equal to the
                    // first byte minus 0xf7 follows the first byte,
                    // and the concatenation of the RLP encodings of all items of
                    // the list follows the total payload of the list;

                    byte lenOfListLen = (byte) (prefix - OFFSET_LONG_LIST);
                    int listLen = calcLength(lenOfListLen, reader);

                    RlpList newLevelList = new RlpList(new ArrayList<>());
                    traverse(reader, listLen, newLevelList);
                    rlpList.getValues().add(newLevelList);

                }
            }
        } catch (Exception e) {
            throw new RuntimeException("RLP wrong encoding", e);
        }
    }

    private static int calcLength(int lengthOfLength, Reader reader) {
        byte pow = (byte) (lengthOfLength - 1);
        long length = 0;
        for (int i = 1; i <= lengthOfLength; ++i) {
            length += reader.getUByte() << (8 * pow);
            pow--;
        }
        if (length < 0 || length > Integer.MAX_VALUE) {
            throw new RuntimeException("RLP too many bytes to decode");
        }
        return (int) length;
    }
}
