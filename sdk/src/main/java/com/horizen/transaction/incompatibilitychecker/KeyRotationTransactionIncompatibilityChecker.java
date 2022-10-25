package com.horizen.transaction.incompatibilitychecker;

import com.horizen.transaction.BoxTransaction;
import com.horizen.transaction.KeyRotationTransaction;

import java.util.List;

public class KeyRotationTransactionIncompatibilityChecker extends DefaultTransactionIncompatibilityChecker {
    @Override
    public <T extends BoxTransaction> boolean isTransactionCompatible(T newTransaction, List<T> memoryPoolTransactions) {
        if (!super.isTransactionCompatible(newTransaction, memoryPoolTransactions)) {
            return false;
        } else if (newTransaction instanceof KeyRotationTransaction) {
            KeyRotationTransaction keyRotationTransaction = (KeyRotationTransaction) newTransaction;
            for (T memoryPoolTransaction: memoryPoolTransactions) {
                if (memoryPoolTransaction instanceof KeyRotationTransaction) {
                    if (keyRotationTransaction.getKeyRotationProof().keyType() == ((KeyRotationTransaction) memoryPoolTransaction).getKeyRotationProof().keyType() &&
                            keyRotationTransaction.getKeyRotationProof().index() == ((KeyRotationTransaction) memoryPoolTransaction).getKeyRotationProof().index()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
