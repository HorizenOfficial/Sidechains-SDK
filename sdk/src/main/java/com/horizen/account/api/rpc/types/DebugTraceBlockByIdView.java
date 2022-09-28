package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.evm.interop.EvmResult;
import com.horizen.serialization.Views;

import java.util.Arrays;

@JsonView(Views.Default.class)
public class DebugTraceBlockByIdView {
    public DebugTraceTransactionView[] debugTraceTransactionViews;

    public DebugTraceBlockByIdView(EvmResult[] evmResults) {
        debugTraceTransactionViews = Arrays.stream(evmResults).map(r -> new DebugTraceTransactionView(r)).toArray(DebugTraceTransactionView[]::new);
    }
}
