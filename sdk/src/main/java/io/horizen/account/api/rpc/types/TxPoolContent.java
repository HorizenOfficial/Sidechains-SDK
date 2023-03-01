package io.horizen.account.api.rpc.types;

import io.horizen.evm.Address;

import java.math.BigInteger;
import java.util.Map;

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
