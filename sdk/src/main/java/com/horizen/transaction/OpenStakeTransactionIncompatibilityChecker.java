package com.horizen.transaction;

import java.util.List;

public class OpenStakeTransactionIncompatibilityChecker extends DefaultTransactionIncompatibilityChecker{
    @Override
    public <T extends BoxTransaction> boolean isTransactionCompatible(T newTx, List<T> currentTxs) {
        if (!super.isTransactionCompatible(newTx, currentTxs)) {
            return false;
        } else if (newTx instanceof OpenStakeTransaction) {
            OpenStakeTransaction openStakeTransaction = (OpenStakeTransaction) newTx;
            for (T mempoolTx: currentTxs) {
                if (mempoolTx instanceof OpenStakeTransaction) {
                    if (openStakeTransaction.getForgerIndex() == ((OpenStakeTransaction) mempoolTx).getForgerIndex()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
