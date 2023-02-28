package io.horizen.account.transaction;

import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.Transaction;
import com.horizen.transaction.exception.TransactionSemanticValidityException;

import java.math.BigInteger;
import java.util.Optional;

public abstract class AccountTransaction<P extends Proposition, PR extends Proof<P>> extends Transaction {

    public abstract void semanticValidity() throws TransactionSemanticValidityException;


    public abstract BigInteger getNonce();

    /* Price per unit of gas a user is willing to pay for their transaction to be executed. */
    public abstract BigInteger getGasPrice();

    public abstract BigInteger getGasLimit();

    public abstract P getFrom();

    public abstract Optional<P> getTo();

    public abstract BigInteger getValue();

    public abstract byte[] getData();

    public abstract PR getSignature();

    /* Maximum fee per unit of gas a user is willing to pay for their transaction to be executed.
    * It must be greater or equal than the sum of the base fee and the priority fee ("tip").
    * It is used together with max priority fee per gas. */
    public abstract BigInteger getMaxFeePerGas();

    /* Fee per unit of gas a user is willing to pay for their transaction to be executed, in addition to base
    fee. It is the part of the gas fee paid directly to the block forger. In case the sum of base fee and priority fee
    is greater than max fee per gas, the user will pay just max fee per gas, thus the effective priority fee will be
    max fee - base fee. */
    public abstract BigInteger getMaxPriorityFeePerGas();


    public abstract BigInteger getEffectiveGasPrice(BigInteger base);
    public abstract BigInteger getPriorityFeePerGas(BigInteger base);

    public BigInteger maxCost() {
            return this.getValue().add(getGasLimit().multiply(getGasPrice()));
    }
}
