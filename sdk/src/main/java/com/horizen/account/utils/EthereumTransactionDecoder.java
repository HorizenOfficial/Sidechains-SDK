package com.horizen.account.utils;

import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.utils.BytesUtils;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.utils.Numeric;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.VLQByteBufferReader;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import static com.horizen.account.utils.EthereumTransactionUtils.convertToLong;
import static com.horizen.account.utils.EthereumTransactionUtils.getRealV;
import static com.horizen.account.utils.Secp256k1.*;


public class EthereumTransactionDecoder {

    private EthereumTransactionDecoder() {
        // prevent instantiation
    }

    // TODO: replace with EthereumTransaction.EthereumTransactionType
    // TODO: detect AccessListTxType during decode -> throw exception
    public enum TransactionType {
        LEGACY(null),
        EIP1559((byte) 2);

        final Byte type;

        TransactionType(Byte type) {
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

    public static EthereumTransaction decode(String hexTransaction) {
        byte[] transaction = Numeric.hexStringToByteArray(hexTransaction);
        Reader reader = new VLQByteBufferReader(ByteBuffer.wrap(transaction));
        EthereumTransaction tx = EthereumTransactionDecoder.decode(reader);
        int size = reader.remaining();
        if (size > 0) {
            byte[] trailingBytes = reader.getBytes(size);
            throw new IllegalArgumentException(
                    "Spurious bytes found in byte stream after obj parsing: [" + BytesUtils.toHexString(trailingBytes) + "]...");
        }
        return tx;
    }

    public static EthereumTransaction decode(Reader reader) {
        byte[] dataByte = new byte[]{reader.peekByte()};

        return getTransactionType(dataByte) == TransactionType.EIP1559 ?
                decodeEIP1559Transaction(reader) : decodeLegacyTransaction(reader); // TODO
    }

    private static TransactionType getTransactionType(byte[] transaction) {
        byte firstByte = transaction[0];
        return firstByte == TransactionType.EIP1559.getRlpType() ? TransactionType.EIP1559 : TransactionType.LEGACY;
    }

    private static EthereumTransaction decodeEIP1559Transaction(Reader reader) {
        // consume the type byte
        reader.getByte();
        RlpList rlpList = RlpStreamDecoder.decode(reader);
        return RlpList2EIP1559Transaction(rlpList);
    }

    private static EthereumTransaction RlpList2EIP1559Transaction(RlpList rlpList) {
        RlpList values = (RlpList)rlpList.getValues().get(0);
        if (values.getValues().size() != 12) {
           throw new IllegalArgumentException(
              "Error while decoding the EIP1559 tx bytes, unexpected number of values in rlp list: " +
               values.getValues().size()
           );
        }

        long chainId = getCheckedValue((RlpString)values.getValues().get(0), "chainId").longValueExact();
        BigInteger nonce = getCheckedValue((RlpString)values.getValues().get(1), "gasNonce");
        BigInteger maxPriorityFeePerGas = getCheckedValue((RlpString)values.getValues().get(2), "maxPriorityFeePerGas");
        BigInteger maxFeePerGas = getCheckedValue((RlpString)values.getValues().get(3), "maxFeePerGas");
        BigInteger gasLimit = getCheckedValue((RlpString)values.getValues().get(4), "gasLimit");

        byte[] toBytes = ((RlpString)values.getValues().get(5)).getBytes();
        Optional<AddressProposition> optTo = EthereumTransactionUtils.getToAddressFromBytes(toBytes);

        BigInteger value = getCheckedValue((RlpString)values.getValues().get(6), "value");

        byte[] dataBytes = ((RlpString)values.getValues().get(7)).getBytes();

        if (((RlpList)values.getValues().get(8)).getValues().size() > 0)
            throw new IllegalArgumentException("Access list is not supported");

        byte[] v = getVFromRecId(Numeric.toBigInt(((RlpString)values.getValues().get(9)).getBytes()).intValueExact());
        byte[] r = ((RlpString)values.getValues().get(10)).getBytes();
        byte[] s = ((RlpString)values.getValues().get(11)).getBytes();

        SignatureSecp256k1 realSignature;
        if (Arrays.equals(r, new byte[0]) && Arrays.equals(s, new byte[0])) {
            // if r and s are both 0 we assume that this signature stands for an unsigned tx object
            // therefore v is the plain chain ID and the signature is set to null
            realSignature = null;
        } else {
            // we check the size here even if an assertion would be thrown when instantiating the signature obj below
            if (r.length != 32)
                throw new IllegalArgumentException("r byte array length: " + r.length + " != 32");
            if (s.length != 32)
                throw new IllegalArgumentException("s byte array length: " + s.length + " != 32");

            realSignature = new SignatureSecp256k1(v, r, s);
        }

        return new EthereumTransaction(
                chainId,
                optTo,
                nonce, gasLimit, maxPriorityFeePerGas, maxFeePerGas, value,
                dataBytes,
                realSignature);

    }

    private static EthereumTransaction decodeLegacyTransaction(Reader reader) {
        RlpList rlpList = RlpStreamDecoder.decode(reader);
        return RlpList2LegacyTransaction(rlpList);
    }

    private static BigInteger getCheckedValue(RlpString rawValueString, String fieldName) {
        byte[] rawValueBytes = rawValueString.getBytes();
        // encoded values should not have prepending zero bytes in the rlp representation
        if (rawValueBytes.length > 1) {
            if (rawValueBytes[0] == 0x00)
            throw new IllegalArgumentException(
                "Error while rlp decoding payload of legacy tx, " + fieldName + " value has leading zero in rlp encoding: " +
                    BytesUtils.toHexString(rawValueBytes)
            );
        }
        return rawValueString.asPositiveBigInteger();
    }

    private static EthereumTransaction RlpList2LegacyTransaction(RlpList rlpList) {

        RlpList values = (RlpList)rlpList.getValues().get(0);
        if (values.getValues().size() != 9) {
            throw new IllegalArgumentException(
                    "Error while rlp decoding payload of legacy tx, unexpected number of values in rlp list: " +
                            values.getValues().size()
            );
        }
        BigInteger nonce = getCheckedValue((RlpString)values.getValues().get(0), "gasNonce");
        BigInteger gasPrice = getCheckedValue((RlpString)values.getValues().get(1), "gasPrice");
        BigInteger gasLimit = getCheckedValue((RlpString)values.getValues().get(2), "gasLimit");

        byte[] toBytes = ((RlpString)values.getValues().get(3)).getBytes();
        Optional<AddressProposition> optTo = EthereumTransactionUtils.getToAddressFromBytes(toBytes);

        BigInteger value = getCheckedValue((RlpString)values.getValues().get(4), "value");

        byte[] dataBytes = ((RlpString)values.getValues().get(5)).getBytes();

        byte[] v = ((RlpString)values.getValues().get(6)).getBytes();
        byte[] r = ((RlpString)values.getValues().get(7)).getBytes();
        byte[] s = ((RlpString)values.getValues().get(8)).getBytes();

        Long chainId;
        SignatureSecp256k1 realSignature;
        if (Arrays.equals(r, new byte[0]) && Arrays.equals(s, new byte[0])) {
            // if r and s are both 0 we assume that this signature stands for an unsigned tx object
            // therefore v is the plain chain ID and the signature is set to null
            chainId = convertToLong(v);
            realSignature = null;
        } else {
            // we check the size here even if an assertion would be thrown when instantiating the signature obj below
            if (r.length != 32)
                throw new IllegalArgumentException("r byte array length: " + r.length + " != 32");
            if (s.length != 32)
                throw new IllegalArgumentException("s byte array length: " + s.length + " != 32");

            chainId = decodeEip155ChainId(v);
            realSignature = new SignatureSecp256k1(getRealV(v), r, s);
        }

        if (chainId != null && chainId != 0) {
            // chain id is encoded into V part, this is an EIP 155
            return new EthereumTransaction(
                    chainId, optTo, nonce, gasPrice, gasLimit, value, dataBytes, realSignature);
        } else {
            return new EthereumTransaction(
                    optTo, nonce, gasPrice, gasLimit, value, dataBytes, realSignature);
        }
    }


    private static Long decodeEip155ChainId(byte[] bv) {
        long v = convertToLong(bv);
        if (v == LOWER_REAL_V || v == (LOWER_REAL_V + 1)) {
            return null;
        }
        return (v - CHAIN_ID_INC) / 2;
    }

    private static byte[] getVFromRecId(int recId) {
        return new byte[]{(byte)(LOWER_REAL_V + recId)};
    }
}
