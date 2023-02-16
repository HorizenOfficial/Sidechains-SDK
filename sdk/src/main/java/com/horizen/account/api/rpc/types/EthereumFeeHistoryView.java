package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.serialization.Views;

import java.math.BigInteger;

@JsonView(Views.Default.class)
public class EthereumFeeHistoryView {
    public final BigInteger oldestBlock;
    public final BigInteger[] baseFeePerGas;
    public final double[] gasUsedRatio;
    public final BigInteger[][] reward;

    public EthereumFeeHistoryView() {
        oldestBlock = BigInteger.ZERO;
        baseFeePerGas = null;
        gasUsedRatio = null;
        reward = null;
    }

    public EthereumFeeHistoryView(
        int oldestBlock,
        BigInteger[] baseFeePerGas,
        double[] gasUsedRatio,
        BigInteger[][] reward
    ) {
        this.oldestBlock = BigInteger.valueOf(oldestBlock);
        this.baseFeePerGas = baseFeePerGas;
        this.gasUsedRatio = gasUsedRatio;
        this.reward = reward;
    }
}
