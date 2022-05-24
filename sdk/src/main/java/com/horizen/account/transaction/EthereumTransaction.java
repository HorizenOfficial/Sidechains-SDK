package com.horizen.account.transaction;

import com.fasterxml.jackson.annotation.*;
import com.google.common.primitives.Bytes;
import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.PublicKeySecp256k1Proposition;
import com.horizen.serialization.Views;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import org.bouncycastle.util.Strings;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@JsonPropertyOrder({"from", "gasPrice", "nonce", "to", "value", "signature"})
@JsonIgnoreProperties({"transaction", "gasLimit"})
@JsonView(Views.Default.class)
public class EthereumTransaction extends AccountTransaction<PublicKeySecp256k1Proposition, SignatureSecp256k1> {

    private final RawTransaction transaction;
    @JsonUnwrapped
    private final PublicKeySecp256k1Proposition from;
    @JsonUnwrapped
    private final SignatureSecp256k1 signature;
    private final int version = 1;

    public EthereumTransaction(RawTransaction transaction,
                               SignatureSecp256k1 signature,
                               PublicKeySecp256k1Proposition from) {
        Objects.requireNonNull(transaction, "Raw Transaction can't be null.");
        Objects.requireNonNull(signature, "Signature can't be null.");
        Objects.requireNonNull(from, "From address can't be null");
        this.transaction = transaction;
        this.signature = signature;
        this.from = from;
    }

    @Override
    public byte transactionTypeId() {
        return AccountTransactionsIdsEnum.EthereumTransaction.id();
    }

    @Override
    public byte version() {
        return version;
    }

    @Override
    public TransactionSerializer serializer() {
        return EthereumTransactionSerializer.getSerializer();
    }

    public RawTransaction getTransaction() {
        return this.transaction;
    }

    @Override
    public void semanticValidity() throws TransactionSemanticValidityException {
        if (transaction.getValue().signum() <= 0) {
            throw new TransactionSemanticValidityException("Cannot create transaction with zero value");
        } else if (transaction.getGasLimit().signum() <= 0) {
            throw new TransactionSemanticValidityException("Cannot create transaction with zero gas limit");
        } else if (from.address().length <= Keys.ADDRESS_LENGTH_IN_HEX + 2) {
            throw new TransactionSemanticValidityException("Cannot create transaction without valid from address");
        } else if (!signature.isValid(from, "test".getBytes(StandardCharsets.UTF_8))) {
            throw new TransactionSemanticValidityException("Cannot create transaction with invalid signature");
        }
    }

    @Override
    public BigInteger getNonce() {
        return transaction.getNonce();
    }

    @Override
    public BigInteger getGasPrice() {
        return transaction.getGasPrice();
    }

    @Override
    public BigInteger getGasLimit() {
        return transaction.getGasLimit();
    }

    @Override
    public PublicKeySecp256k1Proposition getFrom() {
        return from;
    }

    @Override
    public PublicKeySecp256k1Proposition getTo() {
        if (transaction.getTo().length() != Keys.ADDRESS_LENGTH_IN_HEX + 2)
            return null;
        else
            return new PublicKeySecp256k1Proposition(Strings.toByteArray(transaction.getTo()));
    }

    @Override
    public BigInteger getValue() {
        return transaction.getValue();
    }

    @Override
    public String getData() {
        return transaction.getData();
    }

    public SignatureSecp256k1 getSignature() {
        return signature;
    }

    @Override
    public String toString() {
        return String.format(
                "EthereumTransaction{from=%s, nonce=%s, gasPrice=%s, gasLimit=%s, to=%s, data=%s, Signature=%s}",
                Strings.fromByteArray(from.address()),
                Numeric.toHexStringWithPrefix(transaction.getNonce()),
                Numeric.toHexStringWithPrefix(transaction.getGasPrice()),
                Numeric.toHexStringWithPrefix(transaction.getGasLimit()),
                transaction.getTo(),
                transaction.getData(),
                signature.toString()
        );
    }

    @Override
    public byte[] messageToSign() {
        return Bytes.concat(getNonce().toByteArray(),
                getGasPrice().toByteArray(),
                getGasLimit().toByteArray(),
                transaction.getTo().getBytes(StandardCharsets.UTF_8),
                getValue().toByteArray(),
                getData().getBytes(StandardCharsets.UTF_8));
    }
}
