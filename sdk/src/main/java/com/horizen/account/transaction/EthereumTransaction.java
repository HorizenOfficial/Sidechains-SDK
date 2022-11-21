package com.horizen.account.transaction;

import com.fasterxml.jackson.annotation.*;
import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.state.GasUintOverflowException;
import com.horizen.account.state.GasUtil;
import com.horizen.account.state.Message;
import com.horizen.account.utils.Account;
import com.horizen.account.utils.BigIntegerUtil;
import com.horizen.account.utils.EthereumTransactionDecoder;
import com.horizen.account.utils.EthereumTransactionEncoder;
import com.horizen.serialization.Views;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import com.horizen.utils.BytesUtils;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.web3j.crypto.*;
import org.web3j.crypto.Sign.SignatureData;
import org.web3j.utils.Numeric;
import javax.annotation.Nullable;
import java.math.BigInteger;

import static com.horizen.account.utils.EthereumTransactionUtils.*;
import static com.horizen.account.utils.Secp256k1.PUBLIC_KEY_SIZE;


@JsonPropertyOrder({
        "id", "from", "to", "value", "nonce", "data",
        "gasPrice", "gasLimit", "maxFeePerGas", "maxPriorityFeePerGas",
        "eip1559", "version", "chainId", "signed", "signature"
})
@JsonIgnoreProperties({"transaction", "encoder", "modifierTypeId"})
@JsonView(Views.Default.class)
public class EthereumTransaction extends AccountTransaction<AddressProposition, SignatureSecp256k1> {

    //  The 3 versions of tx are supported by go eth and we have test vectors generated using all of them
    //  We are using elsewhere the enum from w3j, which just supports 0 and 2:
    //   org/web3j/crypto/transaction/type/TransactionType.java
    public enum EthereumTransactionType {
        LegacyTxType,     // Legacy
        AccessListTxType, // - not supported
        DynamicFeeTxType  // eip1559
    }

    private final EthereumTransactionType type;
    private final BigInteger nonce;
    private final BigInteger gasPrice;
    private final BigInteger gasLimit;
    private final BigInteger value;
    private final java.lang.Long chainId;
    private final BigInteger maxPriorityFeePerGas;
    private final BigInteger maxFeePerGas;

    private SignatureData signatureData;
    private AddressProposition from;
    private AddressProposition to;
    private byte[] data;

    private void initSignature(SignatureData inSignatureData) {
        if (inSignatureData != null) {
            SignatureSecp256k1.verifySignatureData(inSignatureData.getV(), inSignatureData.getR(), inSignatureData.getS());
            this.signatureData = inSignatureData;
            initFrom();
        } else {
            this.signatureData = null;
        }
    }

    // this initialization relies on the condition that initialization of transaction signature and transaction type
    // data members have already been performed
    private void initFrom() {
        if (this.signatureData != null) {
            Sign.SignatureData inSignatureData = null;

            try {
                Long chainId = getChainId();

                if (isEIP155()) {
                    inSignatureData = createEip155PartialSignatureData(chainId);
                }

                byte[] encodedTransaction = encode(inSignatureData);

                byte[] realV ;
                if (isEIP155())
                    realV = new byte[]{getRealV(Numeric.toBigInt(getSignatureData().getV()))};
                else
                    realV = getSignatureData().getV();
                byte[] r = getSignatureData().getR();
                byte[] s = getSignatureData().getS();

                Sign.SignatureData realSignatureData = new Sign.SignatureData(realV, r, s);
                BigInteger pubKey = Sign.signedMessageToKey(encodedTransaction, realSignatureData);
                this.from = new AddressProposition(Keys.getAddress(Numeric.toBytesPadded(pubKey, PUBLIC_KEY_SIZE)));
            } catch (Exception e) {
                // whatever exception may result in processing the signature, we can not tell the from address
                LogManager.getLogger().info("Could not find from address, Signature not valid:", e);
                this.from = null;
            }
        } else {
            this.from = null;
        }
    }

    private void initTo(String toString) {
        if (toString == null) {
            this.to = null;
        } else {
            String toClean = Numeric.cleanHexPrefix(toString);
            if (toClean.isEmpty()) {
                this.to = null;
            } else {
                // sanity check of formatted string.
                //  Numeric library does not check hex characters' validity, BytesUtils does it
                var toBytes = BytesUtils.fromHexString(toClean);
                if (toBytes.length == 0) {
                    throw new IllegalArgumentException("Invalid input to string: " + toString);
                } else {
                    this.to = new AddressProposition(toBytes);
                }
            }
        }
    }

    private void initData(String dataString) {
        if (dataString == null) {
            this.data = new byte[]{};
        } else {
            String dataStringClean = Numeric.cleanHexPrefix(dataString);
            if (dataStringClean.isEmpty()) {
                this.data = new byte[]{};
            } else {
                // sanity check of formatted string.
                //  Numeric library does not check hex characters' validity, BytesUtils does it
                var dataBytes = BytesUtils.fromHexString(dataStringClean);
                if (dataBytes.length == 0) {
                    throw new IllegalArgumentException("Invalid input to string: " + dataString);
                } else {
                    this.data = dataBytes;
                }
            }
        }
    }


    // creates a legacy transaction
    public EthereumTransaction(
            @NotNull String to,
            @NotNull BigInteger nonce,
            @NotNull BigInteger gasPrice,
            @NotNull BigInteger gasLimit,
            @NotNull BigInteger value,
            @NotNull String data,
            @Nullable SignatureData inSignatureData
    ) {
        this.type = EthereumTransactionType.LegacyTxType;
        this.nonce = nonce;
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
        this.value = value;

        this.chainId = null;
        this.maxPriorityFeePerGas = null;
        this.maxFeePerGas = null;

        initTo(to);
        initData(data);
        initSignature(inSignatureData);
    }


    // creates a legacy eip155 transaction
    public EthereumTransaction(
            @NotNull java.lang.Long chainId,
            @NotNull String to,
            @NotNull BigInteger nonce,
            @NotNull BigInteger gasPrice,
            @NotNull BigInteger gasLimit,
            @NotNull BigInteger value,
            @NotNull String data,
            @Nullable SignatureData inSignatureData
    ) {
        this.type = EthereumTransactionType.LegacyTxType;
        this.nonce = nonce;
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
        this.value = value;
        this.chainId = chainId;

        this.maxPriorityFeePerGas = null;
        this.maxFeePerGas = null;

        initTo(to);
        initData(data);
        initSignature(inSignatureData);
    }

    // creates an eip1559 transaction
    public EthereumTransaction(
            @NotNull java.lang.Long chainId,
            @NotNull String to,
            @NotNull BigInteger nonce,
            @NotNull BigInteger gasLimit,
            @NotNull BigInteger maxPriorityFeePerGas,
            @NotNull BigInteger maxFeePerGas,
            @NotNull BigInteger value,
            @NotNull String data,
            @Nullable SignatureData inSignatureData
    ) {
        this.type = EthereumTransactionType.DynamicFeeTxType;
        this.nonce = nonce;
        this.gasPrice = null;
        this.gasLimit = gasLimit;
        this.value = value;

        this.chainId = chainId;
        this.maxPriorityFeePerGas = maxPriorityFeePerGas;
        this.maxFeePerGas = maxFeePerGas;

        initTo(to);
        initData(data);
        initSignature(inSignatureData);
    }

    // creates a signed transaction from an existing one
    public EthereumTransaction(
            EthereumTransaction txToSign,
            @Nullable SignatureData inSignatureData
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

        initSignature(inSignatureData);
    }

    public boolean isSigned() {
        return (signatureData != null);
    }

    @Override
    public byte transactionTypeId() {
        return AccountTransactionsIdsEnum.EthereumTransactionId.id();
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
        return (byte)this.type.ordinal();
    }

    @Override
    public TransactionSerializer serializer() {
        return EthereumTransactionSerializer.getSerializer();
    }

    @Override
    public void semanticValidity() throws TransactionSemanticValidityException {

        if (!isSigned()) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is not signed", id()));
        }

        if (getChainId() != null && getChainId() < 1L) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] has invalid chainId set: %d", id(), getChainId()));
        }

        if (isEIP155() && !getChainId().equals(EthereumTransactionDecoder.getDecodedChainIdFromSignature(signatureData))) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is eip155 but has incompatible chainId set: %d != encoded in sign=%d",
                    id(), getChainId(), EthereumTransactionDecoder.getDecodedChainIdFromSignature(signatureData)));
        }

        if (Numeric.hexStringToByteArray(getToString()).length != 0) {
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
            if (getDataString().isEmpty())
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
            if (!BigIntegerUtil.isUint256(getMaxFeePerGas()))
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "eip1559 transaction maxFeePerGas bit length [%d] is too high", id(), getMaxFeePerGas().bitLength()));
            if (!BigIntegerUtil.isUint256(getMaxPriorityFeePerGas()))
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
            if (!BigIntegerUtil.isUint256(getGasPrice()))
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
        if (isLegacy())
            return this.gasPrice;
        //in Geth for EIP1559 tx gasPrice returns gasFeeCap
        return this.maxFeePerGas;
    }

    @JsonProperty("gasPrice")
    public BigInteger getJsonGasPrice() {
        if (isLegacy())
            return this.gasPrice;
        // for eip1559 tx this not an attribute of the object, it is computed using baseFee which depends on block height
        return null;
    }

    @Override
    @JsonIgnore
    public BigInteger getMaxFeePerGas() {
        if (isEIP1559())
            return this.maxFeePerGas;
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
        if (isEIP1559() || isEIP155())
            return this.chainId;
        else {
            return null;
        }
    }

    public boolean isEIP1559() {
        return this.type == EthereumTransactionType.DynamicFeeTxType;
    }

    public boolean isLegacy() {
        return this.type == EthereumTransactionType.LegacyTxType;
    }

    public boolean isEIP155() {
        return (isLegacy() && this.chainId != null);
    }

    @Override
    public BigInteger getGasLimit() {
        return this.gasLimit;
    }

    @Override
    public AddressProposition getTo() {
        return this.to;
    }

    @JsonIgnore
    public String getToString() {
        if (this.to != null)
            return BytesUtils.toHexString(this.to.address());
        return "";
    }

    @Override
    public synchronized AddressProposition getFrom() {
        return this.from;
    }

    @JsonIgnore
    public String getFromString() {
        if (this.from != null)
            return BytesUtils.toHexString(this.from.address());
        return "";
    }


    @Override
    public BigInteger getValue() {
        return this.value;
    }

    @Override
    public byte[] getData() {
        return this.data;
    }

    @JsonIgnore
    public String getDataString() {
        return BytesUtils.toHexString(this.data);
    }

    @Override
    public SignatureSecp256k1 getSignature() {
        // TODO use a data member var similar to 'from' to be initialized from the ctor: this code is the same as at line 87/94
        if (signatureData != null) {
            byte[] realV ;
            if (isEIP155())
                realV = new byte[]{getRealV(Numeric.toBigInt(getSignatureData().getV()))};
            else
                realV = getSignatureData().getV();
            return new SignatureSecp256k1(
                    realV,
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
                getFromString(),
                Numeric.toHexStringWithPrefix(getNonce() != null ? getNonce() : BigInteger.ONE.negate()),
                Numeric.toHexStringWithPrefix(getGasLimit() != null ? getGasLimit() : BigInteger.ZERO),
                getToString(),
                Numeric.toHexStringWithPrefix(getValue() != null ? getValue() : BigInteger.ZERO),
                getDataString(),
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
                getFromString(),
                Numeric.toHexStringWithPrefix(getNonce() != null ? getNonce() : BigInteger.ONE.negate()),
                Numeric.toHexStringWithPrefix(getGasPrice() != null ? getGasPrice() : BigInteger.ZERO),
                Numeric.toHexStringWithPrefix(getGasLimit() != null ? getGasLimit() : BigInteger.ZERO),
                getToString(),
                Numeric.toHexStringWithPrefix(getValue() != null ? getValue() : BigInteger.ZERO),
                getDataString(),
                getChainId() != null ? getChainId() : "",
                (int)version(),
                isSigned() ? new SignatureSecp256k1(getSignatureData()).toString() : ""
        );
    }

    @Override
    public byte[] messageToSign() {
        Sign.SignatureData inSignatureData = null;

        if (isEIP155()) {
            Long chainId = getChainId();
            inSignatureData = createEip155PartialSignatureData(chainId);
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

    public byte[] encode(SignatureData inSignatureData) {
        if (this.isEIP1559()) {
            return EthereumTransactionEncoder.encodeEip1559AsRlpValues(this, inSignatureData);
        } else {
            return EthereumTransactionEncoder.encodeLegacyAsRlpValues(this, inSignatureData);
        }
    }
}
