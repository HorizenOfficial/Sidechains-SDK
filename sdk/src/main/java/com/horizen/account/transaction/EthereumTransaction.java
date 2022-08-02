package com.horizen.account.transaction;

import com.fasterxml.jackson.annotation.*;
import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.utils.Account;
import com.horizen.serialization.Views;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import org.jetbrains.annotations.NotNull;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.Sign.SignatureData;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.transaction.type.LegacyTransaction;
import org.web3j.crypto.transaction.type.Transaction1559;
import org.web3j.crypto.transaction.type.TransactionType;
import org.web3j.utils.Numeric;
import org.web3j.crypto.Hash;

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
            if (getMaxFeePerGas().bitCount() > 256)
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "eip1559 transaction maxFeePerGas bit length [%d] is too high", id(), getMaxFeePerGas().bitCount()));

            if (getMaxPriorityFeePerGas().bitCount() > 256)
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "eip1559 transaction maxPriorityFeePerGas bit length [%d] is too high", id(), getMaxPriorityFeePerGas().bitCount()));

            if (getMaxFeePerGas().compareTo(getMaxPriorityFeePerGas()) < 0)
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                                "eip1559 transaction max priority fee per gas [%s] higher than max fee per gas [%s]",
                        id(), getMaxPriorityFeePerGas(), getMaxFeePerGas()));
        } else { // legacy transaction
            if (getGasPrice().signum() < 0)
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "legacy transaction with negative gasPrice", id()));
        }


        //TODO: add this again later or remove, because these checks are already made in some other place
        if (this.getFrom().address().length != Account.ADDRESS_SIZE)
            throw new TransactionSemanticValidityException("Cannot create signed transaction without valid from address");
        //if (!this.getSignature().isValid(this.getFrom(), this.messageToSign()))
        //    throw new TransactionSemanticValidityException("Cannot create signed transaction with invalid " +
        //            "signature");
    }

    @Override
    public BigInteger getNonce() {
        return this.transaction.getNonce();
    }

    @Override
    public BigInteger getGasPrice() {
        if (!this.isEIP1559())
            return this.legacyTx().getGasPrice();
        return null;
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

    static long convertToLong(byte[] bytes) {
        long value = 0l;

        // Iterating through for loop
        for (byte b : bytes) {
            // Shifting previous value 8 bits to right and
            // add it with next value
            value = (value << 8) + (b & 255);
        }

        return value;
    }

    public Long getChainId() {
        if (this.isEIP1559())
            return this.eip1559Tx().getChainId();
        else if (this.isSigned()) {
            var signedTx = (SignedRawTransaction) this.transaction;
            var sigData = signedTx.getSignatureData();
            if (sigData.getS()[0] == 0 && sigData.getR()[0] == 0)
                return convertToLong(sigData.getV());
            else return ((SignedRawTransaction) this.transaction).getChainId(); // useful for EIP155
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

    @Override
    public SignatureSecp256k1 getRealSignature() {
        if (this.isSigned()) {
            SignedRawTransaction stx = (SignedRawTransaction) this.transaction;
            return new SignatureSecp256k1(
                    new byte[]{stx.getRealV(Numeric.toBigInt(stx.getSignatureData().getV()))},
                    stx.getSignatureData().getR(),
                    stx.getSignatureData().getS());
        }
        return null;
    }

    @Override
    public SignatureSecp256k1 getSignature() {
        if (this.isSigned()) {
            SignedRawTransaction stx = (SignedRawTransaction) this.transaction;
            return new SignatureSecp256k1(
                    stx.getSignatureData().getV(),
                    stx.getSignatureData().getR(),
                    stx.getSignatureData().getS());
        }
        return null;
    }


    @Override
    public String toString() {
        if (this.isEIP1559())
            return String.format(
                    "EthereumTransaction{from=%s, nonce=%s, gasLimit=%s, to=%s, value=%s, data=%s, " +
                            "maxFeePerGas=%s, maxPriorityFeePerGas=%s, Signature=%s}",
                    getFromAddress(),
                    Numeric.toHexStringWithPrefix(this.getNonce()),
                    Numeric.toHexStringWithPrefix(this.getGasLimit()),
                    this.getTo() != null ? this.getTo() : "0x",
                    Numeric.toHexStringWithPrefix(this.getValue()),
                    this.getData() != null ? Numeric.toHexString(this.getData()) : "",
                    Numeric.toHexStringWithPrefix(this.getMaxFeePerGas()),
                    Numeric.toHexStringWithPrefix(this.getMaxPriorityFeePerGas()),
                    isSigned() ? getSignature().toString() : ""
            );
        else return String.format(
                "EthereumTransaction{from=%s, nonce=%s, gasPrice=%s, gasLimit=%s, to=%s, value=%s, data=%s, " +
                        "Signature=%s}",
                getFromAddress(),
                Numeric.toHexStringWithPrefix(this.getNonce()),
                Numeric.toHexStringWithPrefix(this.getGasPrice()),
                Numeric.toHexStringWithPrefix(this.getGasLimit()),
                this.getTo() != null ? this.getTo() : "0x",
                Numeric.toHexStringWithPrefix(this.getValue()),
                this.getData() != null ? Numeric.toHexString(this.getData()) : "",
                isSigned() ? getSignature().toString() : ""
        );
    }

    /*
     * from: "0xEB014f8c8B418Db6b45774c326A0E64C78914dC0",
     * gasPrice: "20000000000",
     * gas: "21000",
     * to: '0x3535353535353535353535353535353535353535',
     * value: "1000000000000000000",
     * data: ""
     */
    @Override
    public byte[] messageToSign() {
        if (this.transaction.getType().isLegacy() && this.isSigned())
            return ((SignedRawTransaction) this.transaction).getEncodedTransaction(this.getChainId());
        return TransactionEncoder.encode(this.transaction);
    }
}
