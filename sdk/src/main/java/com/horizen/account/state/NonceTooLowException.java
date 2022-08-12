package com.horizen.account.state;


import com.horizen.transaction.exception.TransactionSemanticValidityException;

import java.math.BigInteger;

public class NonceTooLowException extends TransactionSemanticValidityException {
    public NonceTooLowException(String transactionId, BigInteger nonce) {
        super("Transaction " + transactionId +" is invalid: tx nonce " + nonce + "is too low");
    }

}
