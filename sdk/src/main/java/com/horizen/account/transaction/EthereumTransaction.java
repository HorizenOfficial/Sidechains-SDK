package com.horizen.account.transaction;

import com.fasterxml.jackson.annotation.*;
import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.state.GasUintOverflowException;
import com.horizen.account.state.GasUtil;
import com.horizen.account.state.Message;
import com.horizen.account.utils.Account;
import com.horizen.account.utils.BigIntegerUtil;
import com.horizen.account.utils.EthereumTransactionUtils;
import com.horizen.serialization.Views;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import com.horizen.utils.BytesUtils;
import org.jetbrains.annotations.NotNull;
import org.web3j.crypto.*;
import org.web3j.crypto.Sign.SignatureData;
import org.web3j.crypto.exception.CryptoWeb3jException;
import org.web3j.crypto.transaction.type.TransactionType;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;
import org.web3j.utils.Bytes;
import org.web3j.utils.Numeric;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.web3j.crypto.Sign.CHAIN_ID_INC;
import static org.web3j.crypto.Sign.LOWER_REAL_V;

@JsonPropertyOrder({
        "id", "from", "to", "value", "nonce", "data",
        "gasPrice", "gasLimit", "maxFeePerGas", "maxPriorityFeePerGas",
        "eip1559", "version", "chainId", "signed", "signature"
})
@JsonIgnoreProperties({"transaction", "encoder", "modifierTypeId"})
@JsonView(Views.Default.class)
public class EthereumTransaction extends AccountTransaction<AddressProposition, SignatureSecp256k1> {

    private SignatureSecp256k1 signature;
    private final TransactionType type;
    private final BigInteger nonce;
    private final BigInteger gasPrice;
    private final BigInteger gasLimit;
    private final String to;
    private final BigInteger value;
    private final String data;

    private final java.lang.Long chainId;
    private final BigInteger maxPriorityFeePerGas;
    private final BigInteger maxFeePerGas;

    private static boolean checkSignatureDataSizes(SignatureData data) {
        if (data == null)
            return false;
        return SignatureSecp256k1.checkSignatureDataSizes(
                data.getV(), data.getR(), data.getS());
    }

    private static boolean isPartialEip155Signature(SignatureData data) {
        if (data == null)
            return false;
        return data.getR().length == SignatureSecp256k1.EIP155_PARTIAL_SIGNATURE_RS_SIZE &&
               data.getS().length == SignatureSecp256k1.EIP155_PARTIAL_SIGNATURE_RS_SIZE;
    }

    // creates a signed transaction from an existing one
    public EthereumTransaction(
            EthereumTransaction txToSign,
            @Nullable SignatureData signatureData
    ) {
        this.type = txToSign.type;
        this.nonce = txToSign.nonce;
        this.gasPrice = txToSign.gasPrice;
        this.gasLimit = txToSign.gasLimit;
        this.to = txToSign.to;
        this.value = txToSign.value;
        this.data = txToSign.data;

        this.chainId = txToSign.chainId;
        this.maxPriorityFeePerGas = txToSign.maxPriorityFeePerGas;
        this.maxFeePerGas = txToSign.maxFeePerGas;
        if (signatureData != null) {
            signature = new SignatureSecp256k1(signatureData);
        } else {
            signature = null;
        }
    }

    // creates a legacy transaction
    public EthereumTransaction(
            @Nullable String to,
            @NotNull BigInteger nonce,
            @NotNull BigInteger gasPrice,
            @NotNull BigInteger gasLimit,
            @Nullable BigInteger value,
            @NotNull String data,
            @Nullable SignatureData signatureData
    ) {
        if (signatureData != null) {
            signature = new SignatureSecp256k1(signatureData);
        }
        this.type = TransactionType.LEGACY;
        this.nonce = nonce;
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
        this.to = to;
        this.value = value;
        this.data = data;

        this.chainId = null;
        this.maxPriorityFeePerGas = null;
        this.maxFeePerGas = null;
    }

    // creates an eip1559 transaction
    public EthereumTransaction(
            long chainId,
            @Nullable String to,
            @NotNull BigInteger nonce,
            @NotNull BigInteger gasLimit,
            @NotNull BigInteger maxPriorityFeePerGas,
            @NotNull BigInteger maxFeePerGas,
            @Nullable BigInteger value,
            @Nullable String data,
            @Nullable SignatureData signatureData
    ) {
        if (signatureData != null) {
            signature = new SignatureSecp256k1(signatureData);
        }
        this.type = TransactionType.EIP1559;
        this.nonce = nonce;
        this.gasPrice = null;
        this.gasLimit = gasLimit;
        this.to = to;
        this.value = value;
        this.data = data;

        this.chainId = chainId;
        this.maxPriorityFeePerGas = maxPriorityFeePerGas;
        this.maxFeePerGas = maxFeePerGas;

    }

    public boolean isSigned() {
        return signature != null;
    }

    @Override
    public byte transactionTypeId() {
        return AccountTransactionsIdsEnum.EthereumTransactionId.id();
    }

    public List<RlpType> eip1559AsRlpValues(Sign.SignatureData signatureData) {

        List<RlpType> result = new ArrayList<>();

        result.add(RlpString.create(getChainId()));

        result.add(RlpString.create(getNonce()));

        // add maxPriorityFeePerGas and maxFeePerGas if this is an EIP-1559 transaction
        result.add(RlpString.create(getMaxPriorityFeePerGas()));
        result.add(RlpString.create(getMaxFeePerGas()));

        result.add(RlpString.create(getGasLimit()));

        // an empty to address (contract creation) should not be encoded as a numeric 0 value
        if (to != null && to.length() > 0) {
            // addresses that start with zeros should be encoded with the zeros included, not
            // as numeric values
            result.add(RlpString.create(Numeric.hexStringToByteArray(to)));
        } else {
            result.add(RlpString.create(""));
        }

        result.add(RlpString.create(getValue()));

        // value field will already be hex encoded, so we need to convert into binary first
        byte[] data = Numeric.hexStringToByteArray(this.data);
        result.add(RlpString.create(data));

        // access list
        result.add(new RlpList());

        if (signatureData != null) {
            result.add(RlpString.create(Sign.getRecId(signatureData, getChainId())));
            result.add(RlpString.create(Bytes.trimLeadingZeroes(signatureData.getR())));
            result.add(RlpString.create(Bytes.trimLeadingZeroes(signatureData.getS())));
        }

        return result;
    }

    public List<RlpType> legacyAsRlpValues(Sign.SignatureData signatureData) {

        List<RlpType> result = new ArrayList<>();

        result.add(RlpString.create(this.getNonce()));

        result.add(RlpString.create(this.getGasPrice()));
        result.add(RlpString.create(this.getGasLimit()));

        // an empty to address (contract creation) should not be encoded as a numeric 0 value
        if (this.to != null && this.to.length() > 0) {
            // addresses that start with zeros should be encoded with the zeros included, not
            // as numeric values
            result.add(RlpString.create(Numeric.hexStringToByteArray(to)));
        } else {
            result.add(RlpString.create(""));
        }

        result.add(RlpString.create(getValue()));

        // value field will already be hex encoded, so we need to convert into binary first
        byte[] dataBytes = Numeric.hexStringToByteArray(this.data);
        result.add(RlpString.create(dataBytes));

        if (signatureData != null) {
            result.add(RlpString.create(Bytes.trimLeadingZeroes(signatureData.getV())));
            result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(signatureData.getR())));
            result.add(RlpString.create(EthereumTransactionUtils.trimLeadingZeroes(signatureData.getS())));
        }

        return result;
    }

    @Override
    @JsonProperty("id")
    public String id() {
        byte[] encodedMessage;
        if (isEIP1559()) {
            List<RlpType> values  = this.eip1559AsRlpValues(this.getSignatureData());
            RlpList rlpList = new RlpList(values);
            byte[] encodedMessagePre = RlpEncoder.encode(rlpList);
            encodedMessage = ByteBuffer.allocate(encodedMessagePre.length + 1)
                    .put(this.type.getRlpType())
                    .put(encodedMessagePre)
                    .array();
        } else {
            List<RlpType> values  = this.legacyAsRlpValues(this.getSignatureData());
            RlpList rlpList = new RlpList(values);
            encodedMessage = RlpEncoder.encode(rlpList);
        }
        return BytesUtils.toHexString(Hash.sha3(encodedMessage, 0, encodedMessage.length));
    }

    @Override
    @JsonProperty("version")
    public byte version() {
        if (this.type == TransactionType.LEGACY)
            return 0x0;
        return this.type.getRlpType();
    }

    @Override
    public TransactionSerializer serializer() {
        return EthereumTransactionSerializer.getSerializer();
    }

    @Override
    public void semanticValidity() throws TransactionSemanticValidityException {

        if (!isSigned())
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is not signed", id()));

        if (getToAddress() != null && Numeric.hexStringToByteArray(getToAddress()).length != 0)
        {
            // regular to address

            // sanity check of formatted string.
            String toAddressNoPrefixStr = Numeric.cleanHexPrefix(getToAddress());
            try {
                //  Numeric library does not check hex characters' validity, BytesUtils does it
                if (BytesUtils.fromHexString(toAddressNoPrefixStr).length != Account.ADDRESS_SIZE) {
                    throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "invalid to address length %s", id(), getToAddress()));
                }
            } catch (IllegalArgumentException e) {
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "invalid to address string format %s", id(), getToAddress()));
            }
        } else {
            // contract creation
            if (getData().length == 0)
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "smart contract declaration transaction without data", id()));
        }

        if (getValue().signum() < 0)
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "negative value", id()));
        if (getNonce().signum() < 0)
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "negative nonce", id()));
        if (getGasLimit().signum() <= 0)
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "non-positive gas limit", id()));
        if (!BigIntegerUtil.isUint64(getGasLimit()))
            throw new GasUintOverflowException();

        if (isEIP1559()) {
            if (getMaxFeePerGas().signum() < 0)
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "eip1559 transaction with negative maxFeePerGas", id()));
            if (getMaxPriorityFeePerGas().signum() < 0)
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "eip1559 transaction with negative maxPriorityFeePerGas", id()));
            if (getMaxFeePerGas().bitLength() > 256)
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "eip1559 transaction maxFeePerGas bit length [%d] is too high", id(), getMaxFeePerGas().bitLength()));
            if (getMaxPriorityFeePerGas().bitLength() > 256)
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "eip1559 transaction maxPriorityFeePerGas bit length [%d] is too high", id(), getMaxPriorityFeePerGas().bitLength()));
            if (getMaxFeePerGas().compareTo(getMaxPriorityFeePerGas()) < 0)
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                                "eip1559 transaction maxPriorityFeePerGas [%s] higher than maxFeePerGas [%s]",
                        id(), getMaxPriorityFeePerGas(), getMaxFeePerGas()));
        } else {
            if (getGasPrice().signum() < 0)
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "legacy transaction with negative gasPrice", id()));
            if (getGasPrice().bitLength() > 256)
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "legacy transaction gasPrice bit length [%d] is too high", id(), getGasPrice().bitLength()));
        }
        if (getGasLimit().compareTo(GasUtil.intrinsicGas(getData(), getTo() == null)) < 0) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "gas limit %s is below intrinsic gas %s",
                    id(), getGasLimit(), GasUtil.intrinsicGas(getData(), getTo() == null)));
        }
        try {
            if (!this.getSignature().isValid(this.getFrom(), this.messageToSign()))
                throw new TransactionSemanticValidityException("Cannot create signed transaction with invalid " +
                        "signature");
        } catch (Throwable t) {
            // in case of really malformed signature we can not even compute the id()
            throw new TransactionSemanticValidityException(String.format("Transaction signature not readable: %s", t.getMessage()));
        }

    }

    @Override
    public long size() {
        return serializer().toBytes(this).length;
    }

    @Override
    public BigInteger getNonce() {
        return this.nonce;
    }

    @Override
    @JsonIgnore
    public BigInteger getGasPrice() {
        if (!this.isEIP1559())
            return this.gasPrice;
        //in Geth for EIP1559 tx gasPrice returns gasFeeCap
        return getMaxFeePerGas();
    }

    @JsonProperty("gasPrice")
    public BigInteger getJsonGasPrice() {
        if (!this.isEIP1559())
            return this.gasPrice;
        // for eip1559 tx this not an attribute of the object, it is computed using baseFee which depends on block height
        return null;
    }

    @Override
    @JsonIgnore
    public BigInteger getMaxFeePerGas() {
        if (this.isEIP1559())
            return this.maxFeePerGas;
        else
            //in Geth for Legacy tx gasFeeCap is equal to gasPrice
            return this.gasPrice;
    }

    @JsonProperty("maxFeePerGas")
    public BigInteger getJsonMaxFeePerGas() {
        if (this.isEIP1559())
            return this.maxFeePerGas;
        return null;
    }

    @Override
    @JsonIgnore
    public BigInteger getMaxPriorityFeePerGas() {
        if (this.isEIP1559())
            return this.maxPriorityFeePerGas;
        else
            //in Geth for Legacy tx MaxPriorityFee is equal to gasPrice
            return this.gasPrice;
    }

    @JsonProperty("maxPriorityFeePerGas")
    public BigInteger getJsonMaxPriorityFeePerGas() {
        if (this.isEIP1559())
            return this.maxPriorityFeePerGas;
        return null;
    }

    @Override
    @JsonIgnore
    public BigInteger getMaxCost() {
        if (isEIP1559()) {
            return getValue().add(getGasLimit().multiply(getMaxFeePerGas()));
        } else {
            return getValue().add(getGasLimit().multiply(getGasPrice()));
        }
    }

    @Override
    @JsonIgnore
    public BigInteger getPriorityFeePerGas(BigInteger base) {
        if (isEIP1559()) {
            return getMaxFeePerGas().subtract(base).min(getMaxPriorityFeePerGas());
        } else {
            return getGasPrice().subtract(base);
        }
    }

    @Override
    @JsonIgnore
    public BigInteger getEffectiveGasPrice(BigInteger base) {
        if (this.isEIP1559())
            return base.add(getMaxPriorityFeePerGas()).min(getMaxFeePerGas());
        else
            return getGasPrice();
    }

    public Long getChainId() {
        if (this.isEIP1559())
            return this.chainId;
        else if (this.isSigned()) {
            return getDecodedEip155ChainId();
        }
        return null;
    }

    public boolean isEIP1559() {
        return this.type == TransactionType.EIP1559;
    }

    public Long getDecodedEip155ChainId() {
        if (getSignatureData() != null && getSignatureData().getV() != null) {

            // for a fully signed legacy tx implementing EIP155
            BigInteger bv = Numeric.toBigInt(getSignatureData().getV());
            return decodeEip155ChainId(bv);
        }
        return null;
    }

    public static BigInteger encodeEip155ChainId(Long inChainId) {
        return BigInteger.valueOf(inChainId)
                        .multiply(BigInteger.TWO)
                        .add(BigInteger.valueOf(CHAIN_ID_INC));
    }

    public static Long decodeEip155ChainId(BigInteger inChainId) {
        long v = inChainId.longValue();
        if (v == LOWER_REAL_V || v == (LOWER_REAL_V + 1)) {
            return null;
        }
        return (v - CHAIN_ID_INC) / 2;
    }

    @Override
    public BigInteger getGasLimit() {
        return this.gasLimit;
    }

    @Override
    public AddressProposition getFrom() {
        if (this.isSigned() && checkSignatureDataSizes(getSignatureData()) &&
            !isPartialEip155Signature(getSignatureData())
        ) {
            return new AddressProposition(Numeric.hexStringToByteArray(getFromAddress()));
        }
        return null;
    }

    @Override
    public AddressProposition getTo() {
        String address = getToAddress();
        // In case of smart contract declaration
        if (address == null)
            return null;

        var to = Numeric.hexStringToByteArray(address);
        if (to.length == 0)
            return null;

        if (to.length == Account.ADDRESS_SIZE)
            return new AddressProposition(to);

        throw new RuntimeException(String.format("Invalid to address length %d", to.length));
    }

    @JsonIgnore
    public String getToAddress() {
        return this.to;
    }

    public byte[] encode(SignatureData signatureData) {
        List<RlpType> values;
        if (type.isEip1559()) {
            values = eip1559AsRlpValues(signatureData);
        } else {
            values = legacyAsRlpValues(signatureData);
        }
        RlpList rlpList = new RlpList(values);
        byte[] encoded = RlpEncoder.encode(rlpList);

        if (type.isEip1559()) {
            return ByteBuffer.allocate(encoded.length + 1)
                    .put(type.getRlpType())
                    .put(encoded)
                    .array();
        }
        return encoded;
    }

    private static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    public byte[] encode(Long chainId) {
        if (!type.isLegacy()) {
            throw new CryptoWeb3jException("Incorrect transaction type. Tx type should be Legacy.");
        }

        Sign.SignatureData signatureData =
                new Sign.SignatureData(longToBytes(chainId),
                        SignatureSecp256k1.EIP155_PARTIAL_SIGNATURE_RS, SignatureSecp256k1.EIP155_PARTIAL_SIGNATURE_RS);
        return encode(signatureData);
    }

    public byte[] getEncodedTransaction(Long chainId, SignatureData signatureData) {
        if (null == chainId) {
            return this.encode(signatureData);
        } else {
            return this.encode(chainId);
        }
    }

    private byte getRealV(BigInteger bv) {
        long v = bv.longValue();
        if (v == LOWER_REAL_V || v == (LOWER_REAL_V + 1)) {
            return (byte) v;
        }
        int inc = 0;
        if ((int) v % 2 == 0) {
            inc = 1;
        }
        return (byte) (LOWER_REAL_V + inc);
    }

    @JsonIgnore
    public String getFromAddress() {
        if (this.isSigned()
                && checkSignatureDataSizes(getSignatureData())
                && !isPartialEip155Signature(getSignatureData())
        ) {
            try {
                byte[] encodedTransaction = getEncodedTransaction(getDecodedEip155ChainId(), null);
                BigInteger v = Numeric.toBigInt(getSignatureData().getV());
                byte[] r = getSignatureData().getR();
                byte[] s = getSignatureData().getS();
                Sign.SignatureData signatureDataV = new Sign.SignatureData(getRealV(v), r, s);
                BigInteger key = Sign.signedMessageToKey(encodedTransaction, signatureDataV);
                return "0x" + Keys.getAddress(key);
            } catch (Throwable ignored) {
                // whatever exception may result in processing the signature we return the empty string
            }
        }
        return "";
    }

    @Override
    public BigInteger getValue() {
        return this.value;
    }

    @Override
    public byte[] getData() {
        return Numeric.hexStringToByteArray(this.data);
    }

    @Override
    public SignatureSecp256k1 getSignature() {
        if (this.isSigned() && checkSignatureDataSizes(getSignatureData())) {
            return new SignatureSecp256k1(
                    new byte[]{getRealV(Numeric.toBigInt(getSignatureData().getV()))},
                    getSignatureData().getR(),
                    getSignatureData().getS());
        }
        return null;    }


    @JsonIgnore
    public SignatureData getSignatureData() {
        if (signature != null)
            return new SignatureData(signature.getV(), signature.getR(), signature.getS());
        return null;
    }

    // In case of EIP155 tx getV() returns the value carrying the chainId
    @JsonIgnore
    public byte[] getV() {
        return (getSignatureData() != null) ? getSignatureData().getV() : null;
    }

    @JsonIgnore
    public byte[] getR() {
        return (getSignatureData() != null) ? getSignatureData().getR() : null;
    }

    @JsonIgnore
    public byte[] getS() {
        return (getSignatureData() != null) ? getSignatureData().getS() : null;
    }

    @Override
    public String toString() {

        if (this.isEIP1559())
            return String.format(
                "EthereumTransaction{id=%s, from=%s, nonce=%s, gasLimit=%s, to=%s, value=%s, data=%s, " +
                        "maxFeePerGas=%s, maxPriorityFeePerGas=%s, chainId=%s, version=%d, Signature=%s}",
                id(),
                getFromAddress(),
                Numeric.toHexStringWithPrefix(this.getNonce() != null ? this.getNonce() : BigInteger.ZERO),
                Numeric.toHexStringWithPrefix(this.getGasLimit() != null ? this.getGasLimit() : BigInteger.ZERO),
                this.getToAddress() != null ? this.getToAddress() : "0x",
                Numeric.toHexStringWithPrefix(this.getValue() != null ? this.getValue() : BigInteger.ZERO),
                this.getData() != null ? Numeric.toHexString(this.getData()) : "",
                Numeric.toHexStringWithPrefix(this.getMaxFeePerGas() != null ? this.getMaxFeePerGas() : BigInteger.ZERO),
                Numeric.toHexStringWithPrefix(this.getMaxPriorityFeePerGas() != null ? this.getMaxPriorityFeePerGas() : BigInteger.ZERO),
                this.getChainId() != null ? this.getChainId() : "",
                (int)this.version(),
                (isSigned() && checkSignatureDataSizes(getSignatureData())) ? new SignatureSecp256k1(getSignatureData()).toString() : ""
            );
        else
            return String.format(
                "EthereumTransaction{id=%s, from=%s, nonce=%s, gasPrice=%s, gasLimit=%s, to=%s, value=%s, data=%s, " +
                        "chainId=%s, version=%d, Signature=%s}",
                id(),
                getFromAddress(),
                Numeric.toHexStringWithPrefix(this.getNonce() != null ? this.getNonce() : BigInteger.ZERO),
                Numeric.toHexStringWithPrefix(this.getGasPrice() != null ? this.getGasPrice() : BigInteger.ZERO),
                Numeric.toHexStringWithPrefix(this.getGasLimit() != null ? this.getGasLimit() : BigInteger.ZERO),
                this.getToAddress() != null ? this.getToAddress() : "0x",
                Numeric.toHexStringWithPrefix(this.getValue() != null ? this.getValue() : BigInteger.ZERO),
                this.getData() != null ? Numeric.toHexString(this.getData()) : "",
                this.getChainId() != null ? this.getChainId() : "",
                (int)this.version(),
                (isSigned() && checkSignatureDataSizes(getSignatureData())) ? new SignatureSecp256k1(getSignatureData()).toString() : ""
        );

    }

    @Override
    public byte[] messageToSign() {
        if (this.type.isLegacy() && this.isSigned()) {
            // the chainid might be set also in legacy case due to EIP155
            return this.getEncodedTransaction(getDecodedEip155ChainId(), null /*getSignatureData()*/);
        }
        return this.encode((SignatureData) null /*this.getSignatureData()*/);
    }

    public Message asMessage(BigInteger baseFee) {
        var gasFeeCap = isEIP1559() ? getMaxFeePerGas() : getGasPrice();
        var gasTipCap = isEIP1559() ? getMaxPriorityFeePerGas() : getGasPrice();
        // calculate effective gas price as baseFee + tip capped at the fee cap
        // this will default to gasPrice if the transaction is not EIP-1559
        var effectiveGasPrice = getEffectiveGasPrice(baseFee);
        return new Message(
                getFrom(),
                getTo(),
                effectiveGasPrice,
                gasFeeCap,
                gasTipCap,
                getGasLimit(),
                getValue(),
                getNonce(),
                getData(),
                false
        );
    }
}
