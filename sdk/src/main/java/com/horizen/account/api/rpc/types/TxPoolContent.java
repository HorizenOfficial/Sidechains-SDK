package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.serialization.Views;

import java.math.BigInteger;
import java.util.Map;

@JsonView(Views.Default.class)
public class TxPoolContent {

    public final Map<String, Map<BigInteger,TxPoolTransaction>> pending;
    public final Map<String, Map<BigInteger,TxPoolTransaction>> queued;

    public TxPoolContent(Map<String, Map<BigInteger, TxPoolTransaction>> pending, Map<String, Map<BigInteger, TxPoolTransaction>> queued) {
        this.pending = pending;
        this.queued = queued;
    }
}
