package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.evm.utils.Address;
import com.horizen.serialization.Views;

import java.math.BigInteger;
import java.util.Map;

@JsonView(Views.Default.class)
public class TxPoolContent {
    public final Map<Address, Map<BigInteger, TxPoolTransaction>> pending;
    public final Map<Address, Map<BigInteger, TxPoolTransaction>> queued;

    public TxPoolContent(
        Map<Address, Map<BigInteger, TxPoolTransaction>> pending,
        Map<Address, Map<BigInteger, TxPoolTransaction>> queued
    ) {
        this.pending = pending;
        this.queued = queued;
    }
}
