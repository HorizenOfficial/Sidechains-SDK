package com.horizen.validation.crosschain;

public interface CrossChainValidator<T extends CrossChainBodyToValidate<?>> {
    void validate(T objectToValidate) throws IllegalArgumentException;
}
