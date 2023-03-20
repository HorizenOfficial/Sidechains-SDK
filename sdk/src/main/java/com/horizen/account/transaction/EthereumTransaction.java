package com.horizen.account.transaction;

import com.fasterxml.jackson.annotation.*;
import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.state.GasUtil;
import com.horizen.account.state.Message;
import com.horizen.account.utils.BigIntegerUtil;
import com.horizen.account.utils.EthereumTransactionEncoder;
import com.horizen.account.utils.Secp256k1;
import io.horizen.evm.Address;
import com.horizen.serialization.Views;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import com.horizen.utils.BytesUtils;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.web3j.utils.Numeric;
import sparkz.crypto.hash.Keccak256;
import sparkz.util.ByteArrayBuilder;
import sparkz.util.serialization.VLQByteBufferWriter;
import sparkz.util.serialization.Writer;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Optional;

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

    @JsonProperty("to")
    private final AddressProposition to;

    @JsonProperty("gasPrice")
    private final BigInteger gasPrice;

    private final BigInteger gasLimit;
    private final BigInteger value;
    private final Long chainId;
    private final byte[] data;

    @JsonProperty("maxPriorityFeePerGas")
    private final BigInteger maxPriorityFeePerGas;
    @JsonProperty("maxFeePerGas")
    private final BigInteger maxFeePerGas;

    private final SignatureSecp256k1 signature;

    private AddressProposition from;
    private String hashString;
    private BigInteger txCost;
    private byte[] messageToSign;

    private long size = -1;

    private synchronized String getTxHash() {
        if (this.hashString == null) {
            byte[] encodedMessage;
            if (isSigned())
                encodedMessage = encode(true);
            else
                encodedMessage = this.messageToSign();
            this.hashString = BytesUtils.toHexString((byte[]) Keccak256.hash(encodedMessage));
        }
        return this.hashString;
    }

    @Override
    public synchronized BigInteger maxCost() {
        if (this.txCost == null) {
            this.txCost = super.maxCost();
        }
        return this.txCost;
    }

    // creates a legacy transaction
    public EthereumTransaction(
            @NotNull Optional<AddressProposition> to,
            @NotNull BigInteger nonce,
            @NotNull BigInteger gasPrice,
            @NotNull BigInteger gasLimit,
            @NotNull BigInteger value,
            @NotNull byte[] data,
            @Nullable SignatureSecp256k1 inSignature
    ) {
        this.type = EthereumTransactionType.LegacyTxType;
        this.nonce = nonce;
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
        this.value = value;

        this.chainId = null;
        this.maxPriorityFeePerGas = null;
        this.maxFeePerGas = null;

        this.to = to.orElse(null);
        this.data = data;
        this.signature = inSignature;
    }


    // creates a legacy eip155 transaction
    public EthereumTransaction(
            @NotNull Long chainId,
            @NotNull Optional<AddressProposition> to,
            @NotNull BigInteger nonce,
            @NotNull BigInteger gasPrice,
            @NotNull BigInteger gasLimit,
            @NotNull BigInteger value,
            @NotNull byte[] data,
            @Nullable SignatureSecp256k1 inSignature
    ) {
        this.type = EthereumTransactionType.LegacyTxType;
        this.nonce = nonce;
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
        this.value = value;
        this.chainId = chainId;

        this.maxPriorityFeePerGas = null;
        this.maxFeePerGas = null;

        this.to = to.orElse(null);
        this.data = data;
        this.signature = inSignature;
    }

    // creates an eip1559 transaction
    public EthereumTransaction(
            @NotNull Long chainId,
            @NotNull Optional<AddressProposition> to,
            @NotNull BigInteger nonce,
            @NotNull BigInteger gasLimit,
            @NotNull BigInteger maxPriorityFeePerGas,
            @NotNull BigInteger maxFeePerGas,
            @NotNull BigInteger value,
            @NotNull byte[] data,
            @Nullable SignatureSecp256k1 inSignature
    ) {
        this.type = EthereumTransactionType.DynamicFeeTxType;
        this.nonce = nonce;
        this.gasPrice = null;
        this.gasLimit = gasLimit;
        this.value = value;

        this.chainId = chainId;
        this.maxPriorityFeePerGas = maxPriorityFeePerGas;
        this.maxFeePerGas = maxFeePerGas;

        this.to = to.orElse(null);
        this.data = data;
        this.signature = inSignature;
    }

    // creates a signed transaction from an existing one
    public EthereumTransaction(
            EthereumTransaction txToSign,
            @Nullable SignatureSecp256k1 inSignature
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

        this.signature = inSignature;
    }

    public boolean isSigned() {
        return (signature != null);
    }

    @Override
    public byte transactionTypeId() {
        return AccountTransactionsIdsEnum.EthereumTransactionId.id();
    }

    @Override
    @JsonProperty("id")
    public String id() {
        return getTxHash();
    }

    @Override
    @JsonProperty("version")
    public byte version() {
        return (byte) this.type.ordinal();
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

        // for 'to' address, all checks have been performed during obj initialization
        if (this.getTo().isEmpty()) {
            // contract creation
            if (this.getData().length == 0)
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "smart contract declaration transaction without data", id()));
        }

        if (getValue().signum() < 0)
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "negative value", id()));
        if (getNonce().signum() < 0)
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "negative nonce", id()));
        if (!BigIntegerUtil.isUint64(getNonce()))
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "nonce uint64 overflow", id()));
        if (getGasLimit().signum() <= 0)
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "non-positive gas limit", id()));
        if (!BigIntegerUtil.isUint64(getGasLimit()))
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "gas limit uint64 overflow", id()));

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
        if (getGasLimit().compareTo(GasUtil.intrinsicGas(getData(), getTo().isEmpty())) < 0) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "gas limit %s is below intrinsic gas %s",
                    id(), getGasLimit(), GasUtil.intrinsicGas(getData(), getTo().isEmpty())));
        }

        if (getFrom() == null) {
            // we already checked that this tx is signed, therefore we must be able to get a from address from a valid
            // signature
            throw new TransactionSemanticValidityException("Invalid signature: " + this.getSignature().toString());
        }
    }

    @Override
    @JsonProperty("size")
    public synchronized long size() {
        if (this.size == -1) {
            this.size = serializer().toBytes(this).length;
        }
        return size;
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

    @Override
    @JsonIgnore
    public BigInteger getMaxFeePerGas() {
        if (isEIP1559())
            return this.maxFeePerGas;
        //in Geth for Legacy tx gasFeeCap is equal to gasPrice
        return this.gasPrice;
    }

    @Override
    @JsonIgnore
    public BigInteger getMaxPriorityFeePerGas() {
        if (isEIP1559())
            return this.maxPriorityFeePerGas;
        //in Geth for Legacy tx MaxPriorityFee is equal to gasPrice
        return this.gasPrice;
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

    /**
     * Calculate effective gas price, this will work for both legacy and EIP1559 transactions.
     *
     * @param base base fee applicable for this transaction
     * @return effective gas price
     */
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
    @JsonIgnore
    public Optional<AddressProposition> getTo() {
        return Optional.ofNullable(this.to);
    }

    @JsonIgnore
    public Address getToAddress() {
        if (this.to == null) return null;
        return this.to.address();
    }

    @Override
    public synchronized AddressProposition getFrom() {
        if (this.from == null && this.signature != null) {
            try {
                byte[] message = messageToSign();
                this.from = new AddressProposition(
                        Secp256k1.signedMessageToAddress(
                                message,
                                signature.getV(),
                                signature.getR(),
                                signature.getS()
                        )
                );
            } catch (Exception e) {
                // whatever exception may result in processing the signature, we can not tell the from address
                LogManager.getLogger().info("Could not find from address, Signature not valid:", e);
                this.from = null;
            }
        }
        return this.from;
    }

    @JsonIgnore
    public Address getFromAddress() {
        if (this.getFrom() == null) return null;
        return this.getFrom().address();
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
        if (this.data != null)
            return BytesUtils.toHexString(this.data);
        return "";
    }

    @Override
    public SignatureSecp256k1 getSignature() {
        return this.signature;
    }

    @Override
    public String toString() {
        if (isEIP1559())
            return String.format(
                "EthereumTransaction{id=%s, from=%s, nonce=%s, gasLimit=%s, to=%s, value=%s, data=%s, " +
                        "maxFeePerGas=%s, maxPriorityFeePerGas=%s, chainId=%s, version=%d, Signature=%s}",
                id(),
                getFromAddress(),
                Numeric.toHexStringWithPrefix(getNonce() != null ? getNonce() : BigInteger.ONE.negate()),
                Numeric.toHexStringWithPrefix(getGasLimit() != null ? getGasLimit() : BigInteger.ZERO),
                getToAddress(),
                Numeric.toHexStringWithPrefix(getValue() != null ? getValue() : BigInteger.ZERO),
                getDataString(),
                Numeric.toHexStringWithPrefix(getMaxFeePerGas() != null ? getMaxFeePerGas() : BigInteger.ZERO),
                Numeric.toHexStringWithPrefix(getMaxPriorityFeePerGas() != null ? getMaxPriorityFeePerGas() : BigInteger.ZERO),
                getChainId() != null ? getChainId() : "",
                (int)version(),
                isSigned() ? this.signature.toString() : ""
            );
        else
            return String.format(
                "EthereumTransaction{id=%s, from=%s, nonce=%s, gasPrice=%s, gasLimit=%s, to=%s, value=%s, data=%s, " +
                        "chainId=%s, version=%d, Signature=%s}",
                id(),
                getFromAddress(),
                Numeric.toHexStringWithPrefix(getNonce() != null ? getNonce() : BigInteger.ONE.negate()),
                Numeric.toHexStringWithPrefix(getGasPrice() != null ? getGasPrice() : BigInteger.ZERO),
                Numeric.toHexStringWithPrefix(getGasLimit() != null ? getGasLimit() : BigInteger.ZERO),
                getToAddress(),
                Numeric.toHexStringWithPrefix(getValue() != null ? getValue() : BigInteger.ZERO),
                getDataString(),
                getChainId() != null ? getChainId() : "",
                (int)version(),
                isSigned() ? this.signature.toString() : ""
        );
    }

    @Override
    public synchronized byte[] messageToSign() {
        if (this.messageToSign == null) {
            this.messageToSign = encode(false);
        }
        return this.messageToSign;
    }

    public Message asMessage(BigInteger baseFee) {
        return new Message(
            getFrom() == null ? Address.ZERO : getFrom().address(),
            getTo().map(AddressProposition::address),
            getEffectiveGasPrice(baseFee),
            getMaxFeePerGas(),
            getMaxPriorityFeePerGas(),
            getGasLimit(),
            getValue(),
            getNonce(),
            getData(),
            false
        );
    }

    public byte[] encode(boolean accountSignature) {
        VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
        encode(accountSignature, writer);
        return writer.toBytes();
    }

    public void encode(boolean accountSignature, Writer writer) {
        EthereumTransactionEncoder.encodeAsRlpValues(this, accountSignature, writer);
    }
}
