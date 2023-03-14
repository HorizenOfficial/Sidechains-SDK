package com.horizen.transaction;

import java.util.List;

public class CertificateKeyRotationTransactionIncompatibilityChecker extends DefaultTransactionIncompatibilityChecker {
    @Override
    public <T extends BoxTransaction> boolean isTransactionCompatible(T newTransaction, List<T> memoryPoolTransactions) {
        if (!super.isTransactionCompatible(newTransaction, memoryPoolTransactions)) {
            return false;
        } else if (newTransaction instanceof CertificateKeyRotationTransaction) {
            CertificateKeyRotationTransaction certificateKeyRotationTransaction = (CertificateKeyRotationTransaction) newTransaction;
            for (T memoryPoolTransaction: memoryPoolTransactions) {
                if (memoryPoolTransaction instanceof CertificateKeyRotationTransaction) {
                    if (certificateKeyRotationTransaction.keyRotationProof.keyType() == ((CertificateKeyRotationTransaction) memoryPoolTransaction).keyRotationProof.keyType() &&
                            certificateKeyRotationTransaction.keyRotationProof.index() == ((CertificateKeyRotationTransaction) memoryPoolTransaction).keyRotationProof.index()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
