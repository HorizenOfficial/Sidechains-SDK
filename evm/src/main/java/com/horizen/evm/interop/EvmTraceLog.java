package com.horizen.evm.interop;

import java.util.HashMap;

public class EvmTraceLog {
    public long pc;
    public String op;
    public long gas;
    public long gasCost;
    public int depth;
    public String error;
    public String[] stack;
    public String[] memory;
    public HashMap<String, String> storage;
}
