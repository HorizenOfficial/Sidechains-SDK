package io.horizen.account.api.rpc.types;

import io.horizen.evm.Address;

import java.math.BigInteger;
import java.util.Map;

public class TxPoolInspect {
    public final Map<Address, Map<BigInteger, String>> pending;
    public final Map<Address, Map<BigInteger, String>> queued;

    public TxPoolInspect(
        Map<Address, Map<BigInteger, String>> pending,
        Map<Address, Map<BigInteger, String>> queued
    ) {
        this.pending = pending;
        this.queued = queued;
    }
}
