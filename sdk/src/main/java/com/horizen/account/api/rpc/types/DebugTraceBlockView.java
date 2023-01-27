package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.evm.interop.EvmResult;
import com.horizen.serialization.Views;
import org.apache.logging.log4j.core.util.ArrayUtils;

import java.util.Arrays;
import java.util.Objects;

@JsonView(Views.Default.class)
public class DebugTraceBlockView {
    public DebugTraceTransactionView[] debugTraceTransactionViews;

    public DebugTraceBlockView(EvmResult[] evmResults) {
        EvmResult[] nonNullEvmResults = Arrays.stream(evmResults).filter(Objects::nonNull).toArray(EvmResult[]::new);
        debugTraceTransactionViews = Arrays.stream(nonNullEvmResults).map(DebugTraceTransactionView::new).toArray(DebugTraceTransactionView[]::new);
    }

    public DebugTraceTransactionView[] getDebugTraceTransactionViews() {
        return debugTraceTransactionViews;
    }
}
