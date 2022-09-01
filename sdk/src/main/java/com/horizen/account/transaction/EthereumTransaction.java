package com.horizen.account.transaction;

import com.fasterxml.jackson.annotation.*;
import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.state.GasUintOverflowException;
import com.horizen.account.state.Message;
import com.horizen.account.utils.Account;
import com.horizen.account.utils.BigIntegerUtil;
import com.horizen.account.utils.EthereumTransactionUtils;
import com.horizen.serialization.Views;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import org.jetbrains.annotations.NotNull;
import org.web3j.crypto.*;
import org.web3j.crypto.Sign.SignatureData;
import org.web3j.crypto.transaction.type.LegacyTransaction;
import org.web3j.crypto.transaction.type.Transaction1559;
import org.web3j.crypto.transaction.type.TransactionType;
import org.web3j.utils.Numeric;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.security.SignatureException;
import java.util.Objects;


// TODO ensure that the json parameters are fitting for the use case
@JsonPropertyOrder({"from", "gasPrice", "nonce", "to", "value", "signature"})
@JsonIgnoreProperties({"transaction", "gasLimit"})
@JsonView(Views.Default.class)
public class EthereumTransaction extends AccountTransaction<AddressProposition, SignatureSecp256k1> {

    private final RawTransaction transaction;

    // depends on the transaction
    public EthereumTransaction(
            RawTransaction transaction
    ) throws NullPointerException {
        Objects.requireNonNull(transaction);
        if (transaction instanceof SignedRawTransaction)
            Objects.requireNonNull(((SignedRawTransaction) transaction).getSignatureData());
        this.transaction = transaction;
    }

    // creates a legacy transaction
    public EthereumTransaction(
            @Nullable String to,
            @NotNull BigInteger nonce,
            @NotNull BigInteger gasPrice,
            @NotNull BigInteger gasLimit,
            @Nullable BigInteger value,
            @Nullable String data,
            @Nullable SignatureData signature
    ) {
        this(signature != null ?
                new SignedRawTransaction(
                        RawTransaction.createTransaction(
                                nonce,
                                gasPrice,
                                gasLimit,
                                to != null ? to : "",
                                value != null ? value :
                                        BigInteger.ZERO,
                                data
                        ).getTransaction(),
                        signature) :
                RawTransaction.createTransaction(nonce, gasPrice, gasLimit, to, value, data)
        );
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
            @Nullable SignatureData signature
    ) {
        this(
                signature != null ?
                        new SignedRawTransaction(
                                RawTransaction.createTransaction(chainId, nonce, gasLimit, to != null ? to : "", value != null ? value :
                                        BigInteger.ZERO, data, maxPriorityFeePerGas, maxFeePerGas).getTransaction(), signature)
                        : RawTransaction.createTransaction(chainId, nonce, gasLimit, to != null ? to : "", value != null ? value :
                        BigInteger.ZERO, data, maxPriorityFeePerGas, maxFeePerGas)
        );
    }

    public RawTransaction getTransaction() {
        return this.transaction;
    }

    public boolean isSigned() {
        return this.transaction instanceof SignedRawTransaction;
    }

    @Override
    public byte transactionTypeId() {
        return AccountTransactionsIdsEnum.EthereumTransaction.id();
    }

    @Override
    @JsonProperty("id")
    public String id() {
        byte[] encodedMessage;
        if (this.isSigned()) {
            SignedRawTransaction stx = (SignedRawTransaction) this.transaction;
            encodedMessage = TransactionEncoder.encode(this.getTransaction(),
                    stx.getSignatureData());
        } else encodedMessage = TransactionEncoder.encode(this.getTransaction());
        return com.horizen.utils.BytesUtils.toHexString(Hash.sha3(encodedMessage, 0, encodedMessage.length));
    }

    @Override
    public byte version() {
        if (transaction.getType() == TransactionType.LEGACY)
            return 0x0;
        return 0x2;
    }

    @Override
    public TransactionSerializer serializer() {
        return EthereumTransactionSerializer.getSerializer();
    }

    @Override
    public void semanticValidity() throws TransactionSemanticValidityException {
        if (getValue().signum() < 0)
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "negative value", id()));
        if (getNonce().signum() < 0)
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "negative nonce", id()));
        if (getGasLimit().signum() <= 0)
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "non-positive gas limit", id()));
        if (!BigIntegerUtil.isUint64(getGasLimit())) throw new GasUintOverflowException();
        //TODO why this check is not in Geth (maybe)?
        if (getTo() == null && getData().length == 0)
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "smart contract declaration transaction without data", id()));

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
                                "eip1559 transaction max priority fee per gas [%s] higher than max fee per gas [%s]",
                        id(), getMaxPriorityFeePerGas(), getMaxFeePerGas()));
        } else { // legacy transaction
            if (getGasPrice().signum() < 0)
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "legacy transaction with negative gasPrice", id()));
        }


        if (this.getFrom() == null || this.getFrom().address().length != Account.ADDRESS_SIZE)
            throw new TransactionSemanticValidityException("Cannot create signed transaction without valid from address");
        if (!this.getSignature().isValid(this.getFrom(), this.messageToSign()))
            throw new TransactionSemanticValidityException("Cannot create signed transaction with invalid " +
                    "signature");

        //TODO add intrinsic gas check, if not already made in some other place
    }

    @Override
    public BigInteger getNonce() {
        return this.transaction.getNonce();
    }

    @Override
    public BigInteger getGasPrice() {
        if (!this.isEIP1559())
            return this.legacyTx().getGasPrice();
        //in Geth for EIP1559 tx gasPrice returns gasFeeCap
        return getMaxFeePerGas();
    }

    public BigInteger getMaxFeePerGas() {
        if (this.isEIP1559())
            return this.eip1559Tx().getMaxFeePerGas();
        return null;
    }

    public BigInteger getMaxPriorityFeePerGas() {
        if (this.isEIP1559())
            return this.eip1559Tx().getMaxPriorityFeePerGas();
        return null;
    }

    public Long getChainId() {
        if (this.isEIP1559())
            return this.eip1559Tx().getChainId();
        else if (this.isSigned()) {
            var signedTx = (SignedRawTransaction) this.transaction;
            var sigData = signedTx.getSignatureData();
            if (sigData.getS()[0] == 0 && sigData.getR()[0] == 0) {
                // for a not-really signed legacy tx implementing EIP155, here the chainid is the V itself
                // the caller needs it for encoding the tx properly
                return EthereumTransactionUtils.convertToLong(sigData.getV());
            } else {
                // for a fully signed legacy tx implementing EIP155
                return ((SignedRawTransaction) this.transaction).getChainId();
            }
        }

        return null;
    }

    public boolean isEIP1559() {
        return this.transaction.getTransaction() instanceof Transaction1559;
    }

    private Transaction1559 eip1559Tx() {
        return (Transaction1559) this.transaction.getTransaction();
    }

    private LegacyTransaction legacyTx() {
        return (LegacyTransaction) this.transaction.getTransaction();
    }

    @Override
    public BigInteger getGasLimit() {
        return this.transaction.getGasLimit();
    }

    @Override
    @JsonIgnore
    public AddressProposition getFrom() {
        if (this.isSigned())
            return new AddressProposition(Numeric.hexStringToByteArray(getFromAddress()));
        return null;
    }

    @Override
    public AddressProposition getTo() {
        String address = getToAddress();
        // In case of smart contract declaration
        if (address == null)
            return null;

        // TODO: do we really need the checks below? can we have address of different length? Add more UTs for this tx type.
        // TODO: proabaly we need more checks in semantic validity method
        var to = Numeric.hexStringToByteArray(address);
        if (to.length == 0)
            return null;

        if (to.length == Account.ADDRESS_SIZE)
            return new AddressProposition(to);

        throw new RuntimeException(String.format("Invalid to address length %d", to.length));
    }

    @JsonIgnore
    public String getToAddress() {
        // TODO currently we have a bug that removes 0x prefix during the serialization
        // as a consequence we might have different json strings on different sc network nodes
        return this.transaction.getTo();
    }

    @JsonProperty("from")
    public String getFromAddress() {
        if (this.isSigned()) try {
            return ((SignedRawTransaction) this.transaction).getFrom();
        } catch (SignatureException ignored) {
        }
        return "";
    }

    @Override
    public BigInteger getValue() {
        return this.transaction.getValue();
    }

    //TODO: getData was defined as byte array before, if we want to change to String, please look at all other usages
    @Override
    public byte[] getData() {
        return Numeric.hexStringToByteArray(transaction.getData());
    }

    @JsonIgnore
    @Override
    public SignatureSecp256k1 getSignature() {
        if (this.isSigned()) {
            SignedRawTransaction stx = (SignedRawTransaction) this.transaction;
            return new SignatureSecp256k1(
                    new byte[]{stx.getRealV(Numeric.toBigInt(stx.getSignatureData().getV()))},
                    stx.getSignatureData().getR(),
                    stx.getSignatureData().getS());
        }
        return null;
    }

    @JsonIgnore
    public Sign.SignatureData getSignatureData() {
        if (this.isSigned()) {
            SignedRawTransaction stx = (SignedRawTransaction) this.transaction;
            return new Sign.SignatureData(
                    stx.getSignatureData().getV(),
                    stx.getSignatureData().getR(),
                    stx.getSignatureData().getS());
        }
        return null;
    }

    // used in json representation of signature fields. In case of EIP155 tx getV() returns the value carrying the chainId
    public byte[] getV() { return (getSignatureData() != null) ? getSignatureData().getV() : null; }
    public byte[] getR() { return (getSignatureData() != null) ? getSignatureData().getR() : null; }
    public byte[] getS() { return (getSignatureData() != null) ? getSignatureData().getS() : null; }

    @Override
    public String toString() {
        if (this.isEIP1559())
            return String.format(
                    "EthereumTransaction{id=%s, from=%s, nonce=%s, gasLimit=%s, to=%s, value=%s, data=%s, " +
                            "maxFeePerGas=%s, maxPriorityFeePerGas=%s, Signature=%s}",
                    id(),
                    getFromAddress(),
                    Numeric.toHexStringWithPrefix(this.getNonce()),
                    Numeric.toHexStringWithPrefix(this.getGasLimit()),
                    this.getTo() != null ? this.getTo() : "0x",
                    Numeric.toHexStringWithPrefix(this.getValue()),
                    this.getData() != null ? Numeric.toHexString(this.getData()) : "",
                    Numeric.toHexStringWithPrefix(this.getMaxFeePerGas()),
                    Numeric.toHexStringWithPrefix(this.getMaxPriorityFeePerGas()),
                    isSigned() ? new SignatureSecp256k1(getSignatureData()).toString() : ""
            );
        else return String.format(
                "EthereumTransaction{id=%s, from=%s, nonce=%s, gasPrice=%s, gasLimit=%s, to=%s, value=%s, data=%s, " +
                        "Signature=%s}",
                id(),
                getFromAddress(),
                Numeric.toHexStringWithPrefix(this.getNonce() != null ? this.getNonce() : BigInteger.ZERO),
                Numeric.toHexStringWithPrefix(this.getGasPrice() != null ? this.getGasPrice() : BigInteger.ZERO),
                Numeric.toHexStringWithPrefix(this.getGasLimit() != null ? this.getGasLimit() : BigInteger.ZERO),
                this.getToAddress() != null ? this.getToAddress() : "0x",
                Numeric.toHexStringWithPrefix(this.getValue() != null ? this.getValue() : BigInteger.ZERO),
                this.getData() != null ? Numeric.toHexString(this.getData()) : "",
                isSigned() ? new SignatureSecp256k1(getSignatureData()).toString() : ""
        );
    }

    @Override
    public byte[] messageToSign() {
        if (this.transaction.getType().isLegacy() && this.isSigned()) {
            // the chainid might be set also in legacy case due to EIP155
            return ((SignedRawTransaction) this.transaction).getEncodedTransaction(this.getChainId());
        }
        return TransactionEncoder.encode(this.transaction);
    }

    public Message asMessage(BigInteger baseFee) {
        var is1559 = isEIP1559();
        var gasFeeCap = !is1559 ? getGasPrice() : getMaxFeePerGas();
        var gasTipCap = !is1559 ? getGasPrice() : getMaxPriorityFeePerGas();
        // calculate effective gas price as baseFee + tip capped at the fee cap
        // this will default to gasPrice if the transaction is not EIP-1559
        var effectiveGasPrice = baseFee.add(gasTipCap).min(gasFeeCap);
        return new Message(
                getFrom(),
                getTo(),
                effectiveGasPrice,
                gasFeeCap,
                gasTipCap,
                getGasLimit(),
                getValue(),
                getNonce(),
                getData()
        );
    }
}
