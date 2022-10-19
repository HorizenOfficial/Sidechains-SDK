package com.horizen.transaction.incompatibilitychecker;

import com.horizen.transaction.BoxTransaction;
import com.horizen.transaction.KeyRotationTransaction;

import java.util.List;

public class KeyRotationTransactionIncompatibilityChecker extends DefaultTransactionIncompatibilityChecker {
    @Override
    public <T extends BoxTransaction> boolean isTransactionCompatible(T newTx, List<T> currentTxs) {
        if (!super.isTransactionCompatible(newTx, currentTxs)) {
            return false;
        } else if (newTx instanceof KeyRotationTransaction) {
            KeyRotationTransaction keyRotationTransaction = (KeyRotationTransaction) newTx;
//            for (T mempoolTx: currentTxs) {
//                if (mempoolTx instanceof KeyRotationTransaction) {
//                    if (keyRotationTransaction.getForgerIndex() == ((KeyRotationTransaction) mempoolTx).getForgerIndex()) {
//                        return false;
//                    }
//                }
//            }
        }
        return true;
    }
}
