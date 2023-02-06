package com.horizen.account.utils;

import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.account.proposition.AddressProposition;
import org.bouncycastle.util.BigIntegers;
import org.web3j.rlp.*;
import org.web3j.utils.Numeric;
import scala.Array;
import sparkz.util.serialization.Writer;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.horizen.account.utils.Secp256k1.*;

public class EthereumTransactionEncoder {

    private EthereumTransactionEncoder() {
        // prevent instantiation
    }

    public static byte[] encodeAsRlpValues(EthereumTransaction tx, boolean accountSignature) {
        if (tx.isEIP1559()) {
            return encodeEip1559AsRlpValues(tx, accountSignature);
        } else {
            return encodeLegacyAsRlpValues(tx, accountSignature);
        }
    }

    private static RlpList LegacyTransaction2RlpList(EthereumTransaction tx, boolean accountSignature) {
        List<RlpType> result = new ArrayList<>();

        result.add(RlpString.create(tx.getNonce()));

        result.add(RlpString.create(tx.getGasPrice()));
        result.add(RlpString.create(tx.getGasLimit()));

        // an empty to address (contract creation) should not be encoded as a numeric 0 value
        // addresses that start with zeros should be encoded with the zeros included, not as numeric values
        result.add(RlpString.create(tx.getTo().map(AddressProposition::pubKeyBytes).orElse(Array.emptyByteArray())));
        result.add(RlpString.create(tx.getValue()));

        // value field will already be hex encoded, so we need to convert into binary first
        result.add(RlpString.create(tx.getData()));

        if (accountSignature) {
            if (!tx.isSigned())
                throw new IllegalArgumentException("We should take signature into account for encoding, but tx is not signed!");
            SignatureSecp256k1 txSignature = tx.getSignature();

            byte[] v = txSignature.getV();
            byte[] r = txSignature.getR();
            byte[] s = txSignature.getS();

            if (tx.isEIP155()) {
                v = createEip155v(v, tx.getChainId());
            }

            result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(v)));
            result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(r)));
            result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(s)));
        } else {
            if (tx.isEIP155()) {
                result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(EthereumTransactionUtils.convertToBytes(tx.getChainId()))));
                result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(new byte[] {})));
                result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(new byte[] {})));
            }
        }

        return new RlpList(result);
    }

    private static RlpList EIP1559Transaction2RlpList(EthereumTransaction tx, boolean accountSignature) {
        List<RlpType> result = new ArrayList<>();

        result.add(RlpString.create(tx.getChainId()));
        result.add(RlpString.create(tx.getNonce()));

        // add maxPriorityFeePerGas and maxFeePerGas if this is an EIP-1559 transaction
        result.add(RlpString.create(tx.getMaxPriorityFeePerGas()));
        result.add(RlpString.create(tx.getMaxFeePerGas()));
        result.add(RlpString.create(tx.getGasLimit()));

        // an empty to address (contract creation) should not be encoded as a numeric 0 value
        // addresses that start with zeros should be encoded with the zeros included, not as numeric values
        result.add(RlpString.create(tx.getTo().map(AddressProposition::pubKeyBytes).orElse(Array.emptyByteArray())));
        result.add(RlpString.create(tx.getValue()));

        // value field will already be hex encoded, so we need to convert into binary first
        result.add(RlpString.create(tx.getData()));

        // access list
        result.add(new RlpList());

        if (accountSignature) {
            if (!tx.isSigned())
                throw new IllegalArgumentException("We should take signature into account for encoding, but tx is not signed!");

            SignatureSecp256k1 txSignature = tx.getSignature();
            result.add(RlpString.create(getRecId(txSignature.getV(), tx.getChainId())));
            result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(txSignature.getR())));
            result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(txSignature.getS())));
        }

        return new RlpList(result);
    }

    private static byte[] encodeLegacyAsRlpValues(EthereumTransaction tx, boolean accountSignature) {
        RlpList rlpList = LegacyTransaction2RlpList(tx, accountSignature);
        return RlpEncoder.encode(rlpList);
    }

    private static byte[] encodeEip1559AsRlpValues(EthereumTransaction tx, boolean accountSignature) {
        RlpList rlpList = EIP1559Transaction2RlpList(tx, accountSignature);
        byte[] encoded = RlpEncoder.encode(rlpList);

        return ByteBuffer.allocate(encoded.length + 1)
                .put(tx.version())
                .put(encoded)
                .array();
    }

    public static void encodeAsRlpValues(EthereumTransaction tx, boolean accountSignature, Writer writer) {
        if (tx.isEIP1559()) {
            encodeEip1559AsRlpValues(tx, accountSignature, writer);
        } else {
            encodeLegacyAsRlpValues(tx, accountSignature, writer);
        }
    }

    private static void encodeLegacyAsRlpValues(EthereumTransaction tx, boolean accountSignature, Writer writer) {
        RlpList rlpList = LegacyTransaction2RlpList(tx, accountSignature);
        RlpStreamEncoder.encode(rlpList, writer);
    }

    private static void encodeEip1559AsRlpValues(EthereumTransaction tx, boolean accountSignature, Writer writer) {
        writer.putUByte(tx.version());
        RlpList rlpList = EIP1559Transaction2RlpList(tx, accountSignature);
        RlpStreamEncoder.encode(rlpList, writer);
    }

    private static byte[] createEip155v(byte[] realV, long chainId) {
        // update real `V` field with chain id
        BigInteger v = Numeric.toBigInt(realV);
        v = v.subtract(BigInteger.valueOf(LOWER_REAL_V));
        v = v.add(BigInteger.valueOf(chainId).multiply(BigIntegers.TWO));
        v = v.add(BigInteger.valueOf(CHAIN_ID_INC));

        return v.toByteArray();
    }

    private static int getRecId(byte[] realV, long chainId) {
        BigInteger v = Numeric.toBigInt(realV);
        BigInteger lowerRealV = BigInteger.valueOf(LOWER_REAL_V);
        BigInteger lowerRealVPlus1 = lowerRealV.add(BigInteger.ONE);
        BigInteger lowerRealVReplayProtected = BigInteger.valueOf(REAL_V_REPLAY_PROTECTED);
        BigInteger chainIdInc = BigInteger.valueOf(CHAIN_ID_INC);
        if (!v.equals(lowerRealV) && !v.equals(lowerRealVPlus1)) {
            if (v.compareTo(lowerRealVReplayProtected) >= 0) {
                return v.subtract(BigInteger.valueOf(chainId).multiply(BigIntegers.TWO)).subtract(chainIdInc).intValue();
            } else {
                throw new IllegalArgumentException(String.format("Unsupported v parameter: %s", v));
            }
        } else {
            return v.subtract(lowerRealV).intValue();
        }
    }

}
