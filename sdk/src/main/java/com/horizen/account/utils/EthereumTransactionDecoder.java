package com.horizen.account.utils;

import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.transaction.EthereumTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.transaction.type.TransactionType;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.utils.Numeric;
import java.math.BigInteger;
import java.util.Arrays;

import static com.horizen.account.utils.EthereumTransactionUtils.convertToLong;
import static com.horizen.account.utils.EthereumTransactionUtils.getRealV;
import static org.web3j.crypto.Sign.CHAIN_ID_INC;
import static org.web3j.crypto.Sign.LOWER_REAL_V;

public class EthereumTransactionDecoder {

    public EthereumTransactionDecoder() {
    }

    public static EthereumTransaction decode(String hexTransaction) {
        byte[] transaction = Numeric.hexStringToByteArray(hexTransaction);
        return decode(transaction);
    }
    public static EthereumTransaction decode(byte[] transaction) {
        return getTransactionType(transaction) == TransactionType.EIP1559 ? decodeEIP1559Transaction(transaction) : decodeLegacyTransaction(transaction);
    }

    private static TransactionType getTransactionType(byte[] transaction) {
        byte firstByte = transaction[0];
        return firstByte == TransactionType.EIP1559.getRlpType() ? TransactionType.EIP1559 : TransactionType.LEGACY;
    }

    private static EthereumTransaction decodeEIP1559Transaction(byte[] transaction) {
        byte[] encodedTx = Arrays.copyOfRange(transaction, 1, transaction.length);
        RlpList rlpList = RlpDecoder.decode(encodedTx);
        RlpList values = (RlpList)rlpList.getValues().get(0);
        long chainId = ((RlpString)values.getValues().get(0)).asPositiveBigInteger().longValueExact();
        BigInteger nonce = ((RlpString)values.getValues().get(1)).asPositiveBigInteger();
        BigInteger maxPriorityFeePerGas = ((RlpString)values.getValues().get(2)).asPositiveBigInteger();
        BigInteger maxFeePerGas = ((RlpString)values.getValues().get(3)).asPositiveBigInteger();
        BigInteger gasLimit = ((RlpString)values.getValues().get(4)).asPositiveBigInteger();
        String to = ((RlpString)values.getValues().get(5)).asString();
        BigInteger value = ((RlpString)values.getValues().get(6)).asPositiveBigInteger();
        String data = ((RlpString)values.getValues().get(7)).asString();

        var optTo = EthereumTransactionUtils.getToAddressFromString(to);
        var dataBytes = EthereumTransactionUtils.getDataFromString(data);

        if (((RlpList)values.getValues().get(8)).getValues().size() > 0) throw new IllegalArgumentException("Access list is not supported");
        if (values.getValues().size() == 9) {
            return new EthereumTransaction(
                    chainId,
                    optTo,
                    nonce, gasLimit, maxPriorityFeePerGas, maxFeePerGas, value,
                    dataBytes,
                    null);
        } else {
            byte[] v = Sign.getVFromRecId(Numeric.toBigInt(((RlpString)values.getValues().get(9)).getBytes()).intValueExact());
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

    private static EthereumTransaction decodeLegacyTransaction(byte[] transaction) {
        RlpList rlpList = RlpDecoder.decode(transaction);
        RlpList values = (RlpList)rlpList.getValues().get(0);
        BigInteger nonce = ((RlpString)values.getValues().get(0)).asPositiveBigInteger();
        BigInteger gasPrice = ((RlpString)values.getValues().get(1)).asPositiveBigInteger();
        BigInteger gasLimit = ((RlpString)values.getValues().get(2)).asPositiveBigInteger();
        String to = ((RlpString)values.getValues().get(3)).asString();
        BigInteger value = ((RlpString)values.getValues().get(4)).asPositiveBigInteger();
        String data = ((RlpString)values.getValues().get(5)).asString();

        var optTo = EthereumTransactionUtils.getToAddressFromString(to);
        var dataBytes = EthereumTransactionUtils.getDataFromString(data);

        if (values.getValues().size() != 6 && (values.getValues().size() != 8 || ((RlpString)values.getValues().get(7)).getBytes().length != 10) && (values.getValues().size() != 9 || ((RlpString)values.getValues().get(8)).getBytes().length != 10)) {
            byte[] v = ((RlpString)values.getValues().get(6)).getBytes();
            byte[] r = Numeric.toBytesPadded(Numeric.toBigInt(((RlpString)values.getValues().get(7)).getBytes()), 32);
            byte[] s = Numeric.toBytesPadded(Numeric.toBigInt(((RlpString)values.getValues().get(8)).getBytes()), 32);
            SignatureSecp256k1 realSignature = new SignatureSecp256k1(getRealV(v), r, s);
            Long chainId = decodeEip155ChainId(v);

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

    public static Long decodeEip155ChainId(byte[] bv) {
        long v = convertToLong(bv);
        if (v == LOWER_REAL_V || v == (LOWER_REAL_V + 1)) {
            return null;
        }
        return (v - CHAIN_ID_INC) / 2;
    }
}
