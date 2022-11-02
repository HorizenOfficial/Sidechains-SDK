package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;

@JsonView(Views.Default.class)
public class EthereumFeeHistoryView {
    public final String oldestBlock;
    public final String[] baseFeePerGas;
    public final double[] gasUsedRatio;
    public final String[][] reward;

    public EthereumFeeHistoryView() {
        oldestBlock = Numeric.encodeQuantity(BigInteger.ZERO);
        reward = null;
        baseFeePerGas = null;
        gasUsedRatio = null;
    }

    public EthereumFeeHistoryView(
        int oldestBlock,
        BigInteger[] baseFeePerGas,
        double[] gasUsedRatio,
        BigInteger[][] reward
    ) {
        this.oldestBlock = Numeric.encodeQuantity(BigInteger.valueOf(oldestBlock));
        this.baseFeePerGas = Arrays.stream(baseFeePerGas).map(Numeric::encodeQuantity).toArray(String[]::new);
        this.gasUsedRatio = gasUsedRatio;
        this.reward = Arrays
            .stream(reward)
            .map(nested -> Arrays.stream(nested).map(Numeric::encodeQuantity).toArray(String[]::new))
            .toArray(String[][]::new);
    }
}
