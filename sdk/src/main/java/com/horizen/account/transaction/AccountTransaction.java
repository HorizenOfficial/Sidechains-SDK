package com.horizen.account.transaction;

import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.Transaction;
import com.horizen.transaction.exception.TransactionSemanticValidityException;

public abstract class AccountTransaction<P extends Proposition, PR extends Proof<P>> extends Transaction {
    public abstract void semanticValidity() throws TransactionSemanticValidityException;
}