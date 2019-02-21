package com.horizen.transaction.mainchain;

import scala.util.Try;

public final class CertifierLockSerializer implements SidechainRelatedMainchainTransactionSerializer<CertifierLock>
{
    private static CertifierLockSerializer serializer;

    static {
        serializer = new CertifierLockSerializer();
    }

    private CertifierLockSerializer() {
        super();
    }

    public static CertifierLockSerializer getSerializer() {
        return serializer;
    }

    @Override
    public byte[] toBytes(CertifierLock transaction) {
        return transaction.bytes();
    }

    @Override
    public Try<CertifierLock> parseBytes(byte[] bytes) {
        return CertifierLock.parseBytes(bytes);
    }
}
