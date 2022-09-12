package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.evm.interop.EvmTraceLog;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.HashMap;

@JsonView(Views.Default.class)
public class EthereumStructLog {
    public String pc;
    public String op;
    public String gas;
    public String gasCost;
    public String depth;
    public String error;
    public String[] stack;
    public String[] memory;
    public HashMap<String, String> storage;

    public EthereumStructLog(EvmTraceLog ethereumStructLog) {
        pc = Numeric.toHexStringWithPrefix(BigInteger.valueOf(ethereumStructLog.pc));
        op = ethereumStructLog.op;
        gas = Numeric.toHexStringWithPrefix(BigInteger.valueOf(ethereumStructLog.gas));
        gasCost = Numeric.toHexStringWithPrefix(BigInteger.valueOf(ethereumStructLog.gasCost));
        depth = Numeric.toHexStringWithPrefix(BigInteger.valueOf(ethereumStructLog.depth));
        error = ethereumStructLog.error;
        stack = ethereumStructLog.stack;
        memory = ethereumStructLog.memory;
        storage = ethereumStructLog.storage;
    }
}
