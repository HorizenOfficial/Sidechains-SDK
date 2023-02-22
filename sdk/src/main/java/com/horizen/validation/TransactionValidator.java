package com.horizen.validation;

import com.horizen.transaction.AbstractRegularTransaction;

public interface TransactionValidator<T extends AbstractRegularTransaction> {
    void validate(T txToBeValidated) throws IllegalArgumentException;
}