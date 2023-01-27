package com.horizen.evm.interop;

import java.util.List;

public class EvmCallTraceLog {

    public String type;
    public String from;
    public String to;
    public String value;
    public String gas;
    public String gasUsed;
    public String input;
    public String output;
    public String error;
    public String revertReason;
    public EvmCallTraceLog[] calls;
    
}
