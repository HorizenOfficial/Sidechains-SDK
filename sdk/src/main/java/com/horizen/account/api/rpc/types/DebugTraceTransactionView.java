package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.evm.interop.EvmResult;
import com.horizen.serialization.Views;
import com.horizen.utils.BytesUtils;
import org.web3j.utils.Numeric;

import java.util.Arrays;

@JsonView(Views.Default.class)
public class DebugTraceTransactionView {
    public String gas;
    public String returnValue;
    public Object[] structLogs;

    public DebugTraceTransactionView(EvmResult evmResult) {
        gas = Numeric.toHexStringWithPrefix(evmResult.usedGas);
        returnValue = Numeric.prependHexPrefix(BytesUtils.toHexString(evmResult.returnData));

        structLogs = Arrays.stream(evmResult.traceLogs).map(log -> new EthereumStructLog(log)).toArray();
    }
}