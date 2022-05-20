package com.horizen.account.transaction;

import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.Transaction;
import com.horizen.transaction.exception.TransactionSemanticValidityException;

import java.math.BigInteger;

public abstract class AccountTransaction<P extends Proposition, PR extends Proof<P>> extends Transaction {
    public abstract void semanticValidity() throws TransactionSemanticValidityException;
    public abstract BigInteger getNonce();
    public abstract BigInteger getGasPrice();
    public abstract BigInteger getGasLimit();
    public abstract P getFrom();
    public abstract P getTo();
    public abstract BigInteger getValue();
    public abstract String getData();
    public abstract PR getSignature();
}