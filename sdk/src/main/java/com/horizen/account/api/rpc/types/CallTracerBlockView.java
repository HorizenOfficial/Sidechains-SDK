package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.evm.interop.EvmResult;
import com.horizen.serialization.Views;

import java.util.Arrays;
import java.util.Objects;

@JsonView(Views.Default.class)
public class CallTracerBlockView {

    public CallTracerTransactionView[] callTracerTransactionViews;

    public CallTracerBlockView(EvmResult[] evmResults) {
        EvmResult[] nonNullEvmResults = Arrays.stream(evmResults).filter(Objects::nonNull).toArray(EvmResult[]::new);
        callTracerTransactionViews = Arrays.stream(nonNullEvmResults).map(CallTracerTransactionView::new).toArray(CallTracerTransactionView[]::new);
    }

    public CallTracerTransactionView[] getCallTracerTransactionViews() {
        return callTracerTransactionViews;
    }
}
