package com.horizen.account.utils;

import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.transaction.type.TransactionType;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;

public class EthereumTransactionDecoder {

    private static final int UNSIGNED_EIP1559TX_RLP_LIST_SIZE = 9;

    public EthereumTransactionDecoder() {
    }

    public static RawTransaction decode(String hexTransaction) {
        byte[] transaction = Numeric.hexStringToByteArray(hexTransaction);
        return getTransactionType(transaction) == TransactionType.EIP1559 ? decodeEIP1559Transaction(transaction) : decodeLegacyTransaction(transaction);
    }

    private static TransactionType getTransactionType(byte[] transaction) {
        byte firstByte = transaction[0];
        return firstByte == TransactionType.EIP1559.getRlpType() ? TransactionType.EIP1559 : TransactionType.LEGACY;
    }

    private static RawTransaction decodeEIP1559Transaction(byte[] transaction) {
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
        if (((RlpList)values.getValues().get(8)).getValues().size() > 0) throw new IllegalArgumentException("Access list is not supported");
        RawTransaction rawTransaction = RawTransaction.createTransaction(chainId, nonce, gasLimit, to, value, data, maxPriorityFeePerGas, maxFeePerGas);
        if (values.getValues().size() == 9) {
            return rawTransaction;
        } else {
            byte[] v = Sign.getVFromRecId(Numeric.toBigInt(((RlpString)values.getValues().get(9)).getBytes()).intValueExact());
            byte[] r = Numeric.toBytesPadded(Numeric.toBigInt(((RlpString)values.getValues().get(10)).getBytes()), 32);
            byte[] s = Numeric.toBytesPadded(Numeric.toBigInt(((RlpString)values.getValues().get(11)).getBytes()), 32);
            Sign.SignatureData signatureData = new Sign.SignatureData(v, r, s);
            return new SignedRawTransaction(rawTransaction.getTransaction(), signatureData);
        }
    }

    private static RawTransaction decodeLegacyTransaction(byte[] transaction) {
        RlpList rlpList = RlpDecoder.decode(transaction);
        RlpList values = (RlpList)rlpList.getValues().get(0);
        BigInteger nonce = ((RlpString)values.getValues().get(0)).asPositiveBigInteger();
        BigInteger gasPrice = ((RlpString)values.getValues().get(1)).asPositiveBigInteger();
        BigInteger gasLimit = ((RlpString)values.getValues().get(2)).asPositiveBigInteger();
        String to = ((RlpString)values.getValues().get(3)).asString();
        BigInteger value = ((RlpString)values.getValues().get(4)).asPositiveBigInteger();
        String data = ((RlpString)values.getValues().get(5)).asString();
        if (values.getValues().size() != 6 && (values.getValues().size() != 8 || ((RlpString)values.getValues().get(7)).getBytes().length != 10) && (values.getValues().size() != 9 || ((RlpString)values.getValues().get(8)).getBytes().length != 10)) {
            byte[] v = ((RlpString)values.getValues().get(6)).getBytes();
            byte[] r = Numeric.toBytesPadded(Numeric.toBigInt(((RlpString)values.getValues().get(7)).getBytes()), 32);
            byte[] s = Numeric.toBytesPadded(Numeric.toBigInt(((RlpString)values.getValues().get(8)).getBytes()), 32);
            Sign.SignatureData signatureData = new Sign.SignatureData(v, r, s);
            return new SignedRawTransaction(nonce, gasPrice, gasLimit, to, value, data, signatureData);
        } else {
            return RawTransaction.createTransaction(nonce, gasPrice, gasLimit, to, value, data);
        }
    }
}
