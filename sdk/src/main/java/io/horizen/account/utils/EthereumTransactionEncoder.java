package io.horizen.account.utils;

import io.horizen.account.proof.SignatureSecp256k1;
import io.horizen.account.proposition.AddressProposition;
import io.horizen.account.transaction.EthereumTransaction;
import org.bouncycastle.util.BigIntegers;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;
import scala.Array;
import sparkz.util.serialization.Writer;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static io.horizen.account.utils.Secp256k1.*;

public class EthereumTransactionEncoder {

    private EthereumTransactionEncoder() {
        // prevent instantiation
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
            BigInteger v = BigInteger.ZERO;
            BigInteger r = BigInteger.ZERO;
            BigInteger s = BigInteger.ZERO;

            if (tx.isSigned()) {

                SignatureSecp256k1 txSignature = tx.getSignature();

                v = txSignature.getV();
                r = txSignature.getR();
                s = txSignature.getS();

                if (tx.isEIP155()) {
                    v = createEip155v(v, tx.getChainId());
                }
            } else {
                if (tx.isEIP155()) {
                    v = BigInteger.valueOf(tx.getChainId());
                }
            }

            result.add(RlpString.create(v));
            result.add(RlpString.create(r));
            result.add(RlpString.create(s));
        } else {
            if (tx.isEIP155()) {
                result.add(RlpString.create(BigInteger.valueOf(tx.getChainId())));
                result.add(RlpString.create(BigInteger.ZERO));
                result.add(RlpString.create(BigInteger.ZERO));
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
            BigInteger r = BigInteger.ZERO;
            BigInteger s = BigInteger.ZERO;

            if (tx.isSigned()) {

                SignatureSecp256k1 txSignature = tx.getSignature();
                r = txSignature.getR();
                s = txSignature.getS();
                result.add(RlpString.create(getRecId(txSignature.getV(), tx.getChainId())));

            } else {
                result.add(RlpString.create(tx.getChainId()));
            }
            result.add(RlpString.create(r));
            result.add(RlpString.create(s));
        }

        return new RlpList(result);
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

    private static BigInteger createEip155v(BigInteger v, long chainId) {
        // update real `V` field with chain id
        v = v.subtract(BigInteger.valueOf(LOWER_REAL_V));
        v = v.add(BigInteger.valueOf(chainId).multiply(BigIntegers.TWO));
        v = v.add(BigInteger.valueOf(CHAIN_ID_INC));

        return v;
    }

    private static int getRecId(BigInteger v, long chainId) {
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
