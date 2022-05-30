package com.horizen.account.transaction;

import com.fasterxml.jackson.annotation.*;
import com.google.common.primitives.Bytes;
import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.utils.Account;
import com.horizen.serialization.Views;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Objects;

@JsonPropertyOrder({"from", "gasPrice", "nonce", "to", "value", "signature"})
@JsonIgnoreProperties({"transaction", "gasLimit"})
@JsonView(Views.Default.class)
public class EthereumTransaction extends AccountTransaction<AddressProposition, SignatureSecp256k1> {

    private final RawTransaction transaction;
    private final int version = 1;
    @JsonUnwrapped
    private final SignatureSecp256k1 signature;

    public EthereumTransaction(SignedRawTransaction transaction) {
        Objects.requireNonNull(transaction, "Raw Transaction can't be null.");

        this.transaction = transaction;

        signature = new SignatureSecp256k1(transaction.getSignatureData().getV(),
                transaction.getSignatureData().getR(), transaction.getSignatureData().getS());
    }

    public EthereumTransaction(RawTransaction transaction) {
        Objects.requireNonNull(transaction, "Raw Transaction can't be null.");
        this.transaction = transaction;
        signature = null;
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
        } else {
            try {
                if (signature != null && this.getFrom().address().length <= Account.ADDRESS_SIZE) {
                    throw new TransactionSemanticValidityException("Cannot create signed transaction without valid from address");
                } else if (!signature.isValid(getFrom(), "test".getBytes(StandardCharsets.UTF_8))) {
                    throw new TransactionSemanticValidityException("Cannot create transaction with invalid signature");
                }
            } catch (TransactionSemanticValidityException e) {
                throw new TransactionSemanticValidityException(e);
            }
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
    @JsonUnwrapped
    public AddressProposition getFrom() {
        if (signature != null) {
            SignedRawTransaction tx = (SignedRawTransaction) transaction;
            try {
                return new AddressProposition(Numeric.hexStringToByteArray(tx.getFrom()));
            } catch (SignatureException e) { /*we return null if signature is empty or invalid*/ }
        }
        return null;
    }

    @Override
    public AddressProposition getTo() {
        var to = Numeric.hexStringToByteArray(transaction.getTo());
        if (to.length == Account.ADDRESS_SIZE)
            return new AddressProposition(to);
        return null;
    }

    @Override
    public BigInteger getValue() {
        return transaction.getValue();
    }

    @Override
    public String getData() {
        return transaction.getData();
    }

    @Override
    public SignatureSecp256k1 getSignature() {
        return signature;
    }

    @Override
    public String toString() {
        String address = "";
        if (this.getFrom() != null){ address = Numeric.toHexString(this.getFrom().address()); }
        return String.format(
                "EthereumTransaction{from=%s, nonce=%s, gasPrice=%s, gasLimit=%s, to=%s, data=%s, Signature=%s}",
                address,
                Numeric.toHexStringWithPrefix(transaction.getNonce()),
                Numeric.toHexStringWithPrefix(transaction.getGasPrice()),
                Numeric.toHexStringWithPrefix(transaction.getGasLimit()),
                transaction.getTo(),
                transaction.getData(),
                signature != null ? signature.toString() : ""
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
