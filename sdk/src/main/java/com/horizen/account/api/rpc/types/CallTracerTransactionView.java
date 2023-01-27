package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.evm.interop.EvmCallTraceLog;
import com.horizen.evm.interop.EvmResult;
import com.horizen.serialization.Views;

import java.util.Arrays;

@JsonView(Views.Default.class)
public class CallTracerTransactionView {

    public final String type;
    public final String from;
    public final String to;
    public final String value;
    public final String gas;
    public final String gasUsed;
    public final String input;
    public final String output;
    public final String error;
    public final String revertReason;

    public CallTracerTransactionView[] calls;

    public CallTracerTransactionView(EvmResult evmResult) {
        EvmCallTraceLog evmCallTraceLog = evmResult.callTracerLogs;
        type = evmCallTraceLog.type;
        from = evmCallTraceLog.from;
        to = evmCallTraceLog.to;
        value = evmCallTraceLog.value;
        gas = evmCallTraceLog.gas;
        gasUsed = evmCallTraceLog.gasUsed;
        input = evmCallTraceLog.input;
        output = evmCallTraceLog.output;
        error = evmCallTraceLog.error != null ? evmCallTraceLog.error: "";
        revertReason = evmCallTraceLog.revertReason != null ? evmCallTraceLog.revertReason: "";
        calls = evmCallTraceLog.calls == null ? null : Arrays.stream(evmCallTraceLog.calls).map(CallTracerTransactionView::new).toArray(CallTracerTransactionView[]::new);
    }

    public CallTracerTransactionView(EvmCallTraceLog evmCallTraceLog ) {
        type = evmCallTraceLog.type;
        from = evmCallTraceLog.from;
        to = evmCallTraceLog.to;
        value = evmCallTraceLog.value;
        gas = evmCallTraceLog.gas;
        gasUsed = evmCallTraceLog.gasUsed;
        input = evmCallTraceLog.input;
        output = evmCallTraceLog.output;
        error = evmCallTraceLog.error != null ? evmCallTraceLog.error: "";
        revertReason = evmCallTraceLog.revertReason != null ? evmCallTraceLog.revertReason: "";
    }
}
