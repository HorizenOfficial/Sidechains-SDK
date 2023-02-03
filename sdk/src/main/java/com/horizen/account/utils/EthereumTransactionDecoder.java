package com.horizen.account.utils;

import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.transaction.EthereumTransaction;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.utils.Numeric;
import sparkz.util.serialization.Reader;
import java.math.BigInteger;
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
        return decode(transaction);
    }

    public static EthereumTransaction decode(byte[] transaction) {
        return getTransactionType(transaction) == TransactionType.EIP1559 ? decodeEIP1559Transaction(transaction) : decodeLegacyTransaction(transaction);
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

    private static EthereumTransaction decodeEIP1559Transaction(byte[] transaction) {
        byte[] encodedTx = Arrays.copyOfRange(transaction, 1, transaction.length);
        RlpList rlpList = RlpDecoder.decode(encodedTx);
        return RlpList2EIP1559Transaction(rlpList);
    }

    private static EthereumTransaction RlpList2EIP1559Transaction(RlpList rlpList) {
        RlpList values = (RlpList)rlpList.getValues().get(0);
        if (values.getValues().size() != 9 && values.getValues().size() != 12) {
           throw new IllegalArgumentException(
              "Error while decoding the EIP1559 tx bytes, unexpected number of values in rlp list: " +
               values.getValues().size()
           );
        }
        long chainId = ((RlpString)values.getValues().get(0)).asPositiveBigInteger().longValueExact();
        BigInteger nonce = ((RlpString)values.getValues().get(1)).asPositiveBigInteger();
        BigInteger maxPriorityFeePerGas = ((RlpString)values.getValues().get(2)).asPositiveBigInteger();
        BigInteger maxFeePerGas = ((RlpString)values.getValues().get(3)).asPositiveBigInteger();
        BigInteger gasLimit = ((RlpString)values.getValues().get(4)).asPositiveBigInteger();

        byte[] toBytes = ((RlpString)values.getValues().get(5)).getBytes();
        Optional<AddressProposition> optTo = EthereumTransactionUtils.getToAddressFromBytes(toBytes);

        BigInteger value = ((RlpString)values.getValues().get(6)).asPositiveBigInteger();

        byte[] dataBytes = ((RlpString)values.getValues().get(7)).getBytes();

        if (((RlpList)values.getValues().get(8)).getValues().size() > 0)
            throw new IllegalArgumentException("Access list is not supported");

        if (values.getValues().size() == 9) {
            return new EthereumTransaction(
                    chainId,
                    optTo,
                    nonce, gasLimit, maxPriorityFeePerGas, maxFeePerGas, value,
                    dataBytes,
                    null);
        } else {
            byte[] v = getVFromRecId(Numeric.toBigInt(((RlpString)values.getValues().get(9)).getBytes()).intValueExact());
            byte[] r = Numeric.toBytesPadded(Numeric.toBigInt(((RlpString)values.getValues().get(10)).getBytes()), 32);
            byte[] s = Numeric.toBytesPadded(Numeric.toBigInt(((RlpString)values.getValues().get(11)).getBytes()), 32);
            SignatureSecp256k1 signature = new SignatureSecp256k1(v, r, s);
            return new EthereumTransaction(
                    chainId,
                    optTo,
                    nonce, gasLimit, maxPriorityFeePerGas, maxFeePerGas, value,
                    dataBytes,
                    signature);
        }
    }

    private static EthereumTransaction decodeLegacyTransaction(Reader reader) {
        RlpList rlpList = RlpStreamDecoder.decode(reader);
        return RlpList2LegacyTransaction(rlpList);
    }

    private static EthereumTransaction decodeLegacyTransaction(byte[] transaction) {
        RlpList rlpList = RlpDecoder.decode(transaction);
        return RlpList2LegacyTransaction(rlpList);
    }

    private static EthereumTransaction RlpList2LegacyTransaction(RlpList rlpList) {

        RlpList values = (RlpList)rlpList.getValues().get(0);
        if (values.getValues().size() != 6 && values.getValues().size() != 9) {
            throw new IllegalArgumentException(
                    "Error while rlp decoding payload of legacy tx, unexpected number of values in rlp list: " +
                            values.getValues().size()
            );
        }
        BigInteger nonce = ((RlpString)values.getValues().get(0)).asPositiveBigInteger();
        BigInteger gasPrice = ((RlpString)values.getValues().get(1)).asPositiveBigInteger();
        BigInteger gasLimit = ((RlpString)values.getValues().get(2)).asPositiveBigInteger();

        byte[] toBytes = ((RlpString)values.getValues().get(3)).getBytes();
        Optional<AddressProposition> optTo = EthereumTransactionUtils.getToAddressFromBytes(toBytes);

        BigInteger value = ((RlpString)values.getValues().get(4)).asPositiveBigInteger();

        byte[] dataBytes = ((RlpString)values.getValues().get(5)).getBytes();

        if (values.getValues().size() != 6 ) {
            byte[] v = ((RlpString)values.getValues().get(6)).getBytes();
            // we chose to pad byte arrays shorter than 32 bytes. The method throws an exception if it is longer
            byte[] r = Numeric.toBytesPadded(Numeric.toBigInt(((RlpString)values.getValues().get(7)).getBytes()), 32);
            byte[] s = Numeric.toBytesPadded(Numeric.toBigInt(((RlpString)values.getValues().get(8)).getBytes()), 32);

            Long chainId;
            SignatureSecp256k1 realSignature;
            if (Arrays.equals(r, new byte[32]) && Arrays.equals(s, new byte[32])) {
                // if r and s are both 0 we assume that this signature stands for an unsigned eip155 tx object
                // therefore v is the plain chain ID and the signature is set to null
                chainId = convertToLong(v);
                realSignature = null;
            } else {
                chainId = decodeEip155ChainId(v);
                realSignature = new SignatureSecp256k1(getRealV(v), r, s);
            }

            if (chainId != null) {
                // chain id is encoded into V part, this is an EIP 155
                return new EthereumTransaction(
                        chainId, optTo, nonce, gasPrice, gasLimit, value, dataBytes, realSignature);
            } else {
                return new EthereumTransaction(
                        optTo, nonce, gasPrice, gasLimit, value, dataBytes, realSignature);
            }
        } else {
            return new EthereumTransaction(
                    optTo, nonce, gasPrice, gasLimit, value, dataBytes, null);
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
