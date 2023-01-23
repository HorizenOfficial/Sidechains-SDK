package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.evm.interop.EvmTraceLog;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.HashMap;

@JsonView(Views.Default.class)
public class EthereumStructLog {
    public final String pc;
    public final String op;
    public final String gas;
    public final String gasCost;
    public final String depth;
    public final String error;
    public final String[] stack;
    public final String[] memory;
    public final HashMap<String, String> storage;

    public EthereumStructLog(EvmTraceLog ethereumStructLog) {
        pc = Numeric.toHexStringWithPrefix(BigInteger.valueOf(ethereumStructLog.pc));
        op = ethereumStructLog.op;
        gas = Numeric.toHexStringWithPrefix(BigInteger.valueOf(ethereumStructLog.gas));
        gasCost = Numeric.toHexStringWithPrefix(BigInteger.valueOf(ethereumStructLog.gasCost));
        depth = Numeric.toHexStringWithPrefix(BigInteger.valueOf(ethereumStructLog.depth));
        error = ethereumStructLog.error != null ? ethereumStructLog.error : "";
        stack = ethereumStructLog.stack != null ? ethereumStructLog.stack : new String[]{""};
        memory = ethereumStructLog.memory != null ? ethereumStructLog.memory : new String[]{""};
        storage = ethereumStructLog.storage != null ? ethereumStructLog.storage : new HashMap<>();
    }
}
