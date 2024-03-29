package io.horizen.account.transaction;

import io.horizen.proof.Proof;
import io.horizen.proposition.Proposition;
import io.horizen.transaction.Transaction;
import io.horizen.transaction.exception.TransactionSemanticValidityException;

import java.math.BigInteger;
import java.util.Optional;

public abstract class AccountTransaction<P extends Proposition, PR extends Proof<P>> extends Transaction {

    public abstract void semanticValidity() throws TransactionSemanticValidityException;

    public abstract void semanticValidity(int consensusEpochNumber) throws TransactionSemanticValidityException;

    public abstract BigInteger getNonce();

    /**
     * Price per unit of gas a user is willing to pay for their transaction to be executed.
     */
    public abstract BigInteger getGasPrice();

    public abstract BigInteger getGasLimit();

    public abstract P getFrom();

    public abstract Optional<P> getTo();

    public abstract BigInteger getValue();

    public abstract byte[] getData();

    public abstract PR getSignature();

    /**
     * Maximum fee per unit of gas a user is willing to pay for their transaction to be executed. Effective fee per unit
     * of gas is base fee plus priority fee, but capped by this value.
     */
    public abstract BigInteger getMaxFeePerGas();

    /**
     * Fee per unit of gas a user is willing to pay for their transaction to be executed, in addition to base fee. It is
     * the part of the gas fee paid directly to the block forger. In case the sum of base fee and max priority fee is
     * greater than max fee per gas, the user will pay just max fee per gas, thus the effective priority fee is reduced
     * to max fee minus base fee.
     */
    public abstract BigInteger getMaxPriorityFeePerGas();

    /**
     * Effective fee per unit of gas, which equals the base fee plus the max priority fee, capped at max fee.
     */
    public abstract BigInteger getEffectiveGasPrice(BigInteger base);

    public abstract BigInteger getPriorityFeePerGas(BigInteger base);

    public BigInteger maxCost() {
        return this.getValue().add(getGasLimit().multiply(getGasPrice()));
    }
}
