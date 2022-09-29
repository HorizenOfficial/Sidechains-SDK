package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.evm.interop.EvmResult;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;

import java.util.Arrays;

@JsonView(Views.Default.class)
public class DebugTraceTransactionView {
    public final String gas;
    public final String returnValue;
    public final EthereumStructLog[] structLogs;

    public DebugTraceTransactionView(EvmResult evmResult) {
        if (evmResult == null) {
            gas = null;
            returnValue = null;
            structLogs = null;
            return;
        }
        gas = evmResult.usedGas == null ? null : Numeric.encodeQuantity(evmResult.usedGas);
        returnValue = evmResult.returnData == null ? null : Numeric.toHexString(evmResult.returnData);
        structLogs = evmResult.traceLogs == null ? null : Arrays.stream(evmResult.traceLogs).map(EthereumStructLog::new).toArray(EthereumStructLog[]::new);
    }
}
