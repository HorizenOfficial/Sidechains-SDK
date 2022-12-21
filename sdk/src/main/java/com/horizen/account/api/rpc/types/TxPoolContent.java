package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.serialization.Views;

import java.math.BigInteger;
import java.util.Map;

@JsonView(Views.Default.class)
public class TxPoolContent {

    private final Map<String, Map<BigInteger,TxPoolTransaction>> pending;
    private final Map<String, Map<BigInteger,TxPoolTransaction>> queued;

    public TxPoolContent(Map<String, Map<BigInteger, TxPoolTransaction>> pending, Map<String, Map<BigInteger, TxPoolTransaction>> queued) {
        this.pending = pending;
        this.queued = queued;
    }

    public Map<String, Map<BigInteger, TxPoolTransaction>> getPending() {
        return pending;
    }

    public Map<String, Map<BigInteger, TxPoolTransaction>> getQueued() {
        return queued;
    }
}