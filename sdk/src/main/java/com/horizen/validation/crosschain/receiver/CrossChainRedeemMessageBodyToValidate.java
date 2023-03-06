package com.horizen.validation.crosschain.receiver;

import com.horizen.box.data.CrossChainRedeemMessageBoxData;
import com.horizen.transaction.AbstractCrossChainRedeemTransaction;
import com.horizen.validation.crosschain.CrossChainBodyToValidate;

public class CrossChainRedeemMessageBodyToValidate implements CrossChainBodyToValidate<CrossChainRedeemMessageBoxData> {
    private final AbstractCrossChainRedeemTransaction tx;

    public CrossChainRedeemMessageBodyToValidate(AbstractCrossChainRedeemTransaction tx) {
        this.tx = tx;
    }

    @Override
    public CrossChainRedeemMessageBoxData getBodyToValidate() {
        return tx.getRedeemMessageBox();
    }
}
