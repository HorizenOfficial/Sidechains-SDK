package com.horizen.node;

public interface NodeState {

    void semanticValidity();

    void closedBox();

    void boxesOf();

    void changes();

    void validate();

    void validateMC2SCAggregatedTx();

    void validateWithdrawalRequestTx();

    void applyChanges();

    void rollbackTo();

}
