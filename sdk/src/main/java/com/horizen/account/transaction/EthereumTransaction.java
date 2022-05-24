package com.horizen.account.transaction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.PublicKeySecp256k1Proposition;
import com.horizen.account.utils.Secp256k1;
import com.horizen.serialization.Views;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Objects;


@JsonView(Views.Default.class)
public class EthereumTransaction extends AccountTransaction<PublicKeySecp256k1Proposition, SignatureSecp256k1> {

    @JsonIgnoreProperties({"legacyTransaction", "eip1559Transaction"})
    private final RawTransaction transaction;
    private final PublicKeySecp256k1Proposition from;
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
        return new PublicKeySecp256k1Proposition(Numeric.hexStringToByteArray(transaction.getTo()));
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
        return this.signature;
    }

    @Override
    public String toString() {
        return String.format(
                "EthereumTransaction{nonce=%s, gasPrice=%s, gasLimit=%s, to=%s, data=%s, Signature=%s}",
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
        return new byte[0];
    }
}
