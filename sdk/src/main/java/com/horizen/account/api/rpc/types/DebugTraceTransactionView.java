package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.evm.interop.EvmResult;
import com.horizen.serialization.Views;
import com.horizen.utils.BytesUtils;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;

@JsonView(Views.Default.class)
public class DebugTraceTransactionView {
    public String gas;
    public String returnValue;
    public EthereumStructLog[] structLogs;

    public DebugTraceTransactionView(EvmResult evmResult) {
        if (evmResult != null) {
            if (evmResult.usedGas != null) {
                gas = Numeric.toHexStringWithPrefix(evmResult.usedGas);
            }
            if (evmResult.returnData != null) {
                returnValue = Numeric.prependHexPrefix(BytesUtils.toHexString(evmResult.returnData));
            }
            if (evmResult.traceLogs != null) {
                structLogs = Arrays.stream(evmResult.traceLogs).map(log -> new EthereumStructLog(log)).toArray(EthereumStructLog[]::new);
            }
        }
    }
}