package com.horizen.account.utils;

import com.horizen.account.transaction.EthereumTransaction;
import org.web3j.crypto.Sign;
import org.web3j.rlp.*;
import org.web3j.utils.Bytes;
import org.web3j.utils.Numeric;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import static org.web3j.crypto.Sign.CHAIN_ID_INC;

public class EthereumTransactionEncoder {

    public EthereumTransactionEncoder() {
    }

    public static byte[] encodeLegacyAsRlpValues(EthereumTransaction tx, Sign.SignatureData signatureData) {

        List<RlpType> result = new ArrayList<>();

        result.add(RlpString.create(tx.getNonce()));

        result.add(RlpString.create(tx.getGasPrice()));
        result.add(RlpString.create(tx.getGasLimit()));

        // an empty to address (contract creation) should not be encoded as a numeric 0 value
        if (tx.getToString() != null && tx.getToString().length() > 0) {
            // addresses that start with zeros should be encoded with the zeros included, not
            // as numeric values
            result.add(RlpString.create(Numeric.hexStringToByteArray(tx.getToString())));
        } else {
            result.add(RlpString.create(""));
        }

        result.add(RlpString.create(tx.getValue()));

        // value field will already be hex encoded, so we need to convert into binary first
        byte[] dataBytes = tx.getData();
        result.add(RlpString.create(dataBytes));

        if (signatureData != null) {
            result.add(RlpString.create(Bytes.trimLeadingZeroes(signatureData.getV())));
            result.add(RlpString.create(Bytes.trimLeadingZeroes(signatureData.getR())));
            result.add(RlpString.create(Bytes.trimLeadingZeroes(signatureData.getS())));
        }

        RlpList rlpList = new RlpList(result);
        return RlpEncoder.encode(rlpList);
    }

    public static byte[] encodeEip1559AsRlpValues(EthereumTransaction tx, Sign.SignatureData signatureData) {

        List<RlpType> result = new ArrayList<>();

        result.add(RlpString.create(tx.getChainId()));

        result.add(RlpString.create(tx.getNonce()));

        // add maxPriorityFeePerGas and maxFeePerGas if this is an EIP-1559 transaction
        result.add(RlpString.create(tx.getMaxPriorityFeePerGas()));
        result.add(RlpString.create(tx.getMaxFeePerGas()));

        result.add(RlpString.create(tx.getGasLimit()));

        // an empty to address (contract creation) should not be encoded as a numeric 0 value
        if (tx.getToString() != null && tx.getToString().length() > 0) {
            // addresses that start with zeros should be encoded with the zeros included, not
            // as numeric values
            result.add(RlpString.create(Numeric.hexStringToByteArray(tx.getToString())));
        } else {
            result.add(RlpString.create(""));
        }

        result.add(RlpString.create(tx.getValue()));

        // value field will already be hex encoded, so we need to convert into binary first
        byte[] data = Numeric.hexStringToByteArray(tx.getDataString());
        result.add(RlpString.create(data));

        // access list
        result.add(new RlpList());

        if (signatureData != null) {
            result.add(RlpString.create(Sign.getRecId(signatureData, tx.getChainId())));
            result.add(RlpString.create(Bytes.trimLeadingZeroes(signatureData.getR())));
            result.add(RlpString.create(Bytes.trimLeadingZeroes(signatureData.getS())));
        }

        RlpList rlpList = new RlpList(result);
        byte[] encoded = RlpEncoder.encode(rlpList);

        return ByteBuffer.allocate(encoded.length + 1)
                .put(tx.version())
                .put(encoded)
                .array();
    }

}
