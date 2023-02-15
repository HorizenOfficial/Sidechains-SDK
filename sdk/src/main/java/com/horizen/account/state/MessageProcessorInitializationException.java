package com.horizen.account.state;


public class MessageProcessorInitializationException extends Exception {
    public MessageProcessorInitializationException(String errorMessage) {
        super(errorMessage);
    }

    public MessageProcessorInitializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public MessageProcessorInitializationException(Throwable cause) {
        super(cause);
    }
}
