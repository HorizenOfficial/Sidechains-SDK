package io.horizen.utxo.crosschain;

public interface CrossChainBodyToValidate<T> {
    T getBodyToValidate();
}
