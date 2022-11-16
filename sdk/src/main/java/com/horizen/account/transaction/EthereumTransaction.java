package com.horizen.account.transaction;

import com.fasterxml.jackson.annotation.*;
import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.state.GasUintOverflowException;
import com.horizen.account.state.GasUtil;
import com.horizen.account.state.Message;
import com.horizen.account.utils.Account;
import com.horizen.account.utils.BigIntegerUtil;
import com.horizen.account.utils.EthereumTransactionEncoder;
import com.horizen.serialization.Views;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import com.horizen.utils.BytesUtils;
import org.jetbrains.annotations.NotNull;
import org.web3j.crypto.*;
import org.web3j.crypto.Sign.SignatureData;
import org.web3j.crypto.exception.CryptoWeb3jException;
import org.web3j.crypto.transaction.type.TransactionType;
import org.web3j.utils.Numeric;
import javax.annotation.Nullable;
import java.math.BigInteger;
import static com.horizen.account.utils.EthereumTransactionDecoder.getDecodedChainIdFromSignature;
import static com.horizen.account.utils.EthereumTransactionUtils.*;


@JsonPropertyOrder({
        "id", "from", "to", "value", "nonce", "data",
        "gasPrice", "gasLimit", "maxFeePerGas", "maxPriorityFeePerGas",
        "eip1559", "version", "chainId", "signed", "signature"
})
@JsonIgnoreProperties({"transaction", "encoder", "modifierTypeId"})
@JsonView(Views.Default.class)
public class EthereumTransaction extends AccountTransaction<AddressProposition, SignatureSecp256k1> {

    private SignatureData signatureData;

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

    public boolean isPartiallyEip155Signed() {
        return (signatureData != null && // if this is not null, we alreay enforce v/r/s are not null
                signatureData.getR().length == SignatureSecp256k1.EIP155_PARTIAL_SIGNATURE_RS_SIZE &&
                signatureData.getS().length == SignatureSecp256k1.EIP155_PARTIAL_SIGNATURE_RS_SIZE);
    }

    private void initSignature(SignatureData inSignatureData) {
        if (inSignatureData != null) {
            SignatureSecp256k1.verifySignatureData(inSignatureData.getV(), inSignatureData.getR(), inSignatureData.getS());
            this.signatureData = inSignatureData;
        } else {
            this.signatureData = null;
        }
    }

    // creates a signed transaction from an existing one
    public EthereumTransaction(
            EthereumTransaction txToSign,
            @Nullable SignatureData inSignatureData
    ) {
        initSignature(inSignatureData);

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

    }

    // creates a legacy transaction
    public EthereumTransaction(
            @Nullable String to,
            @NotNull BigInteger nonce,
            @NotNull BigInteger gasPrice,
            @NotNull BigInteger gasLimit,
            @Nullable BigInteger value,
            @NotNull String data,
            @Nullable SignatureData inSignatureData
    ) {
        initSignature(inSignatureData);

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
            @Nullable SignatureData inSignatureData
    ) {
        initSignature(inSignatureData);

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
        return (signatureData != null && !isPartiallyEip155Signed());
    }

    @Override
    public byte transactionTypeId() {
        return AccountTransactionsIdsEnum.EthereumTransactionId.id();
    }

    @JsonIgnore
    public TransactionType getType() {
        return this.type;
    }

    @Override
    @JsonProperty("id")
    public String id() {
        byte[] encodedMessage = encode(getSignatureData());
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

        if (getToString() != null && Numeric.hexStringToByteArray(getToString()).length != 0)
        {
            // regular to address

            // sanity check of formatted string.
            String toAddressNoPrefixStr = Numeric.cleanHexPrefix(getToString());
            try {
                //  Numeric library does not check hex characters' validity, BytesUtils does it
                if (BytesUtils.fromHexString(toAddressNoPrefixStr).length != Account.ADDRESS_SIZE) {
                    throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "invalid to address length %s", id(), getToString()));
                }
            } catch (IllegalArgumentException e) {
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "invalid to address string format %s", id(), getToString()));
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
            if (!getSignature().isValid(getFrom(), messageToSign()))
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
        if (!isEIP1559())
            return this.gasPrice;
        //in Geth for EIP1559 tx gasPrice returns gasFeeCap
        return getMaxFeePerGas();
    }

    @JsonProperty("gasPrice")
    public BigInteger getJsonGasPrice() {
        if (!isEIP1559())
            return this.gasPrice;
        // for eip1559 tx this not an attribute of the object, it is computed using baseFee which depends on block height
        return null;
    }

    @Override
    @JsonIgnore
    public BigInteger getMaxFeePerGas() {
        if (isEIP1559())
            return this.maxFeePerGas;
        else
            //in Geth for Legacy tx gasFeeCap is equal to gasPrice
            return this.gasPrice;
    }

    @JsonProperty("maxFeePerGas")
    public BigInteger getJsonMaxFeePerGas() {
        if (isEIP1559())
            return this.maxFeePerGas;
        return null;
    }

    @Override
    @JsonIgnore
    public BigInteger getMaxPriorityFeePerGas() {
        if (isEIP1559())
            return this.maxPriorityFeePerGas;
        else
            //in Geth for Legacy tx MaxPriorityFee is equal to gasPrice
            return this.gasPrice;
    }

    @JsonProperty("maxPriorityFeePerGas")
    public BigInteger getJsonMaxPriorityFeePerGas() {
        if (isEIP1559())
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
        if (isEIP1559())
            return base.add(getMaxPriorityFeePerGas()).min(getMaxFeePerGas());
        else
            return getGasPrice();
    }

    public Long getChainId() {
        if (isEIP1559())
            return this.chainId;
        else if (signatureData != null) {
            return getDecodedChainIdFromSignature(signatureData);
        }
        return null;
    }

    public boolean isEIP1559() {
        return this.type == TransactionType.EIP1559;
    }

    public boolean isLegacy() {
        return this.type == TransactionType.LEGACY;
    }

    @Override
    public BigInteger getGasLimit() {
        return this.gasLimit;
    }

    @Override
    public AddressProposition getFrom() {
        if (isSigned()) {
            return new AddressProposition(Numeric.hexStringToByteArray(getFromAddress()));
        }
        return null;
    }

    @Override
    public AddressProposition getTo() {
        String address = getToString();
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
    public String getToString() {
        return this.to;
    }

    public byte[] encode(SignatureData inSignatureData) {
        if (type.isEip1559()) {
            return EthereumTransactionEncoder.encodeEip1559AsRlpValues(this, inSignatureData);
        } else {
            return EthereumTransactionEncoder.encodeLegacyAsRlpValues(this, inSignatureData);
        }
    }


    @JsonIgnore
    public String getFromAddress() {
        if (isSigned()) {
            Sign.SignatureData inSignatureData = null;

            try {
                Long encodedChainId = getDecodedChainIdFromSignature(getSignatureData());

                if (isLegacy()) {
                    if (encodedChainId != null) {
                        // we have eip155 transaction
                        inSignatureData = new Sign.SignatureData(
                                convertToBytes(encodedChainId),
                                SignatureSecp256k1.EIP155_PARTIAL_SIGNATURE_RS,
                                SignatureSecp256k1.EIP155_PARTIAL_SIGNATURE_RS);

                    }
                } else {
                    if (encodedChainId != null)
                        throw new CryptoWeb3jException("Incorrect transaction type.");
                }

                byte[] encodedTransaction = encode(inSignatureData);

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

    @JsonIgnore
    public String getDataString() {
        return this.data;
    }

    @Override
    public SignatureSecp256k1 getSignature() {
        if (signatureData != null) {
            return new SignatureSecp256k1(
                    new byte[]{getRealV(Numeric.toBigInt(getSignatureData().getV()))},
                    getSignatureData().getR(),
                    getSignatureData().getS());
        }
        return null;
    }


    @JsonIgnore
    public SignatureData getSignatureData() {
        return signatureData;
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

        if (isEIP1559())
            return String.format(
                "EthereumTransaction{id=%s, from=%s, nonce=%s, gasLimit=%s, to=%s, value=%s, data=%s, " +
                        "maxFeePerGas=%s, maxPriorityFeePerGas=%s, chainId=%s, version=%d, Signature=%s}",
                id(),
                getFromAddress(),
                Numeric.toHexStringWithPrefix(getNonce() != null ? getNonce() : BigInteger.ZERO),
                Numeric.toHexStringWithPrefix(getGasLimit() != null ? getGasLimit() : BigInteger.ZERO),
                getToString() != null ? getToString() : "0x",
                Numeric.toHexStringWithPrefix(getValue() != null ? getValue() : BigInteger.ZERO),
                getData() != null ? Numeric.toHexString(getData()) : "",
                Numeric.toHexStringWithPrefix(getMaxFeePerGas() != null ? getMaxFeePerGas() : BigInteger.ZERO),
                Numeric.toHexStringWithPrefix(getMaxPriorityFeePerGas() != null ? getMaxPriorityFeePerGas() : BigInteger.ZERO),
                getChainId() != null ? getChainId() : "",
                (int)version(),
                isSigned() ? new SignatureSecp256k1(getSignatureData()).toString() : ""
            );
        else
            return String.format(
                "EthereumTransaction{id=%s, from=%s, nonce=%s, gasPrice=%s, gasLimit=%s, to=%s, value=%s, data=%s, " +
                        "chainId=%s, version=%d, Signature=%s}",
                id(),
                getFromAddress(),
                Numeric.toHexStringWithPrefix(getNonce() != null ? getNonce() : BigInteger.ZERO),
                Numeric.toHexStringWithPrefix(getGasPrice() != null ? getGasPrice() : BigInteger.ZERO),
                Numeric.toHexStringWithPrefix(getGasLimit() != null ? getGasLimit() : BigInteger.ZERO),
                getToString() != null ? getToString() : "0x",
                Numeric.toHexStringWithPrefix(getValue() != null ? getValue() : BigInteger.ZERO),
                getData() != null ? Numeric.toHexString(getData()) : "",
                getChainId() != null ? getChainId() : "",
                (int)version(),
                isSigned() ? new SignatureSecp256k1(getSignatureData()).toString() : ""
        );

    }

    @Override
    public byte[] messageToSign() {
        Sign.SignatureData inSignatureData = null;

        if (isLegacy() && signatureData != null) {

            Long encodedChainId = getDecodedChainIdFromSignature(getSignatureData());
            if (encodedChainId != null) {
                // we have eip155 transaction
                inSignatureData = new Sign.SignatureData(
                        convertToBytes(encodedChainId),
                        SignatureSecp256k1.EIP155_PARTIAL_SIGNATURE_RS,
                        SignatureSecp256k1.EIP155_PARTIAL_SIGNATURE_RS);

            }
        }
        return encode(inSignatureData);
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
