package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.serialization.Views;

@JsonView(Views.Default.class)
public class EthereumFeeHistoryView {
    public final String oldestBlock;
    public final String[][] reward;
    public final String[] baseFeePerGas;
    public final String[] gasUsedRatio;

    public EthereumFeeHistoryView(String oldestBlock, String[][] reward, String[] baseFeePerGas, String[] gasUsedRatio) {
        this.oldestBlock = oldestBlock;
        this.reward = reward;
        this.baseFeePerGas = baseFeePerGas;
        this.gasUsedRatio = gasUsedRatio;
    }
}
