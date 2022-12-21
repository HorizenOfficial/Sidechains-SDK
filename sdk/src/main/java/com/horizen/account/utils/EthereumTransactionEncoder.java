package com.horizen.account.utils;

import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.transaction.EthereumTransaction;
import org.web3j.crypto.Sign;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;
import scala.Array;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.horizen.account.utils.EthereumTransactionUtils.convertToBytes;
import static org.web3j.crypto.TransactionEncoder.createEip155SignatureData;

public class EthereumTransactionEncoder {

    public EthereumTransactionEncoder() {
    }

    public static byte[] encodeLegacyAsRlpValues(EthereumTransaction tx, boolean accountSignature) {

        List<RlpType> result = new ArrayList<>();

        result.add(RlpString.create(tx.getNonce()));

        result.add(RlpString.create(tx.getGasPrice()));
        result.add(RlpString.create(tx.getGasLimit()));

        // an empty to address (contract creation) should not be encoded as a numeric 0 value
        // addresses that start with zeros should be encoded with the zeros included, not as numeric values
        result.add(RlpString.create(tx.getTo().map(AddressProposition::address).orElse(Array.emptyByteArray())));
        result.add(RlpString.create(tx.getValue()));

        // value field will already be hex encoded, so we need to convert into binary first
        result.add(RlpString.create(tx.getData()));

        if (accountSignature) {
            if (!tx.isSigned())
                throw new IllegalArgumentException("We should take signature into account for encoding, but tx is not signed!");
            Sign.SignatureData signatureData;
            if (tx.isEIP155()) {
                signatureData = createEip155SignatureData(tx.getSignature().getSignatureData(), tx.getChainId());
            } else {
                signatureData = tx.getSignature().getSignatureData();
            }
            result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(signatureData.getV())));
            result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(signatureData.getR())));
            result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(signatureData.getS())));
        } else {
            if (tx.isEIP155()) {
                result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(convertToBytes(tx.getChainId()))));
                result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(new byte[] {})));
                result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(new byte[] {})));
            }
        }

        RlpList rlpList = new RlpList(result);
        return RlpEncoder.encode(rlpList);
    }

    public static byte[] encodeEip1559AsRlpValues(EthereumTransaction tx, boolean accountSignature) {

        List<RlpType> result = new ArrayList<>();

        result.add(RlpString.create(tx.getChainId()));
        result.add(RlpString.create(tx.getNonce()));

        // add maxPriorityFeePerGas and maxFeePerGas if this is an EIP-1559 transaction
        result.add(RlpString.create(tx.getMaxPriorityFeePerGas()));
        result.add(RlpString.create(tx.getMaxFeePerGas()));
        result.add(RlpString.create(tx.getGasLimit()));

        // an empty to address (contract creation) should not be encoded as a numeric 0 value
        // addresses that start with zeros should be encoded with the zeros included, not as numeric values
        result.add(RlpString.create(tx.getTo().map(AddressProposition::address).orElse(Array.emptyByteArray())));
        result.add(RlpString.create(tx.getValue()));

        // value field will already be hex encoded, so we need to convert into binary first
        result.add(RlpString.create(tx.getData()));

        // access list
        result.add(new RlpList());

        if (accountSignature) {
            if (!tx.isSigned())
                throw new IllegalArgumentException("We should take signature into account for encoding, but tx is not signed!");
            Sign.SignatureData signatureData = tx.getSignature().getSignatureData();
            result.add(RlpString.create(Sign.getRecId(signatureData, tx.getChainId())));
            result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(signatureData.getR())));
            result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(signatureData.getS())));
        }

        RlpList rlpList = new RlpList(result);
        byte[] encoded = RlpEncoder.encode(rlpList);

        return ByteBuffer.allocate(encoded.length + 1)
                .put(tx.version())
                .put(encoded)
                .array();
    }
}
