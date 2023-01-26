package com.horizen.account.utils;

import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import scorex.util.serialization.Reader;

import java.util.ArrayList;

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
        if (reader != null)
            traverse(reader, rlpList, reader.consumed(),-1);
        return rlpList;
    }

    private static long traverse(Reader reader, RlpList rlpList, int startConsumedBytes, int levelBytesToRead) {

        try {
            if (reader == null || reader.remaining() == 0) {
                return 0L;
            }

            while (true) {

                int cons = reader.consumed();

                if (levelBytesToRead < 0) {
                    if (cons > startConsumedBytes) {
                       return cons;
                    }
                } else {
                    if ((startConsumedBytes + levelBytesToRead) == cons) {
                        return cons;
                    }
                }

                //int prefix = data[startPos] & 0xff;
                byte[] dataByte = new byte[]{reader.getByte()};
                int prefix = dataByte[0] & 0xff;

                if (prefix < OFFSET_SHORT_STRING) {

                    // 1. the data is a string if the range of the
                    // first byte(i.e. prefix) is [0x00, 0x7f],
                    // and the string is the first byte itself exactly;

                    //byte[] rlpData = {(byte) prefix};
                    byte[] rlpData = {(byte) prefix};
                    rlpList.getValues().add(RlpString.create(rlpData));
                    //startPos += 1;

                } else if (prefix == OFFSET_SHORT_STRING) {

                    // null
                    rlpList.getValues().add(RlpString.create(new byte[0]));
                    //startPos += 1;

                } else if (prefix > OFFSET_SHORT_STRING && prefix <= OFFSET_LONG_STRING) {

                    // 2. the data is a string if the range of the
                    // first byte is [0x80, 0xb7], and the string
                    // which length is equal to the first byte minus 0x80
                    // follows the first byte;

                    byte strLen = (byte) (prefix - OFFSET_SHORT_STRING);

                    // Input validation
                    //if (strLen > endPos - (startPos + 1)) {
                    if (reader.remaining() < strLen) {
                        throw new RuntimeException("RLP length mismatch");
                    }

                    //byte[] rlpData = new byte[strLen];
                    //System.arraycopy(data, startPos + 1, rlpData, 0, strLen);
                    byte[] rlpData = reader.getBytes(strLen);
                    rlpList.getValues().add(RlpString.create(rlpData));

                    //startPos += 1 + strLen;


                } else if (prefix > OFFSET_LONG_STRING && prefix < OFFSET_SHORT_LIST) {

                    // 3. the data is a string if the range of the
                    // first byte is [0xb8, 0xbf], and the length of the
                    // string which length in bytes is equal to the
                    // first byte minus 0xb7 follows the first byte,
                    // and the string follows the length of the string;

                    byte lenOfStrLen = (byte) (prefix - OFFSET_LONG_STRING);
                    //int strLen = calcLength(lenOfStrLen, data, startPos);
                    int strLen = calcLength(lenOfStrLen, reader);

                    // Input validation
                    //if (strLen > endPos - (startPos + lenOfStrLen + 1)) {
                    if (reader.remaining() < strLen) {
                        throw new RuntimeException(
                                "RLP length mismatch: remaining bytes in stream reader: " + reader.remaining() +
                                ", wanted: " + strLen);
                    }

                    // now we can parse an item for data[1]..data[length]
                    //byte[] rlpData = new byte[strLen];
                    //System.arraycopy(data, startPos + lenOfStrLen + 1, rlpData, 0, strLen);
                    byte[] rlpData = reader.getBytes(strLen);

                    rlpList.getValues().add(RlpString.create(rlpData));
                    //startPos += lenOfStrLen + strLen + 1;

                } else if (prefix >= OFFSET_SHORT_LIST && prefix <= OFFSET_LONG_LIST) {

                    // 4. the data is a list if the range of the
                    // first byte is [0xc0, 0xf7], and the concatenation of
                    // the RLP encodings of all items of the list which the
                    // total payload is equal to the first byte minus 0xc0 follows the first byte;

                    byte listLen = (byte) (prefix - OFFSET_SHORT_LIST);

                    RlpList newLevelList = new RlpList(new ArrayList<>());
                    //traverse(data, startPos + 1, startPos + listLen + 1, newLevelList);
                    int dum = reader.consumed();
                    traverse(reader, newLevelList, reader.consumed(), listLen);

                    rlpList.getValues().add(newLevelList);

                    //startPos += 1 + listLen;

                } else if (prefix > OFFSET_LONG_LIST) {

                    // 5. the data is a list if the range of the
                    // first byte is [0xf8, 0xff], and the total payload of the
                    // list which length is equal to the
                    // first byte minus 0xf7 follows the first byte,
                    // and the concatenation of the RLP encodings of all items of
                    // the list follows the total payload of the list;

                    byte lenOfListLen = (byte) (prefix - OFFSET_LONG_LIST);
                    //int listLen = calcLength(lenOfListLen, data, startPos);
                    int listLen = calcLength(lenOfListLen, reader);

                    RlpList newLevelList = new RlpList(new ArrayList<>());
                    /*traverse(
                            data,
                            startPos + lenOfListLen + 1,
                            startPos + lenOfListLen + listLen + 1,
                            newLevelList);
                    */
                    int dum = reader.consumed();
                    traverse(reader, newLevelList, dum, listLen);

                    rlpList.getValues().add(newLevelList);

                    //startPos += lenOfListLen + listLen + 1;
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
            byte[] dataByte = new byte[]{reader.getByte()};
            long val = dataByte[0] & 0xff;
            length += ((long) val) << (8 * pow);
            pow--;
        }
        if (length < 0 || length > Integer.MAX_VALUE) {
            throw new RuntimeException("RLP too many bytes to decode");
        }
        return (int) length;
    }
}

