package io.horizen.validation;

import io.horizen.utxo.transaction.AbstractRegularTransaction;

public interface TransactionValidator<T extends AbstractRegularTransaction> {
    void validate(T txToBeValidated) throws Exception;
}