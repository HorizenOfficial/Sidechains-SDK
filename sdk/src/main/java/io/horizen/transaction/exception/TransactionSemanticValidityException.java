package io.horizen.transaction.exception;

public class TransactionSemanticValidityException extends Exception {
    public TransactionSemanticValidityException() {
        super();
    }

    public TransactionSemanticValidityException(String message) {
        super(message);
    }

    public TransactionSemanticValidityException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransactionSemanticValidityException(Throwable cause) {
        super(cause);
    }

    protected TransactionSemanticValidityException(String message, Throwable cause,
                                                   boolean enableSuppression,
                                                   boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
