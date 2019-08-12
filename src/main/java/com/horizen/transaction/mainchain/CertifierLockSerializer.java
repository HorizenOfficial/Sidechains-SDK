package com.horizen.transaction.mainchain;

import scala.util.Try;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class CertifierLockSerializer implements SidechainRelatedMainchainOutputSerializer<CertifierLock>
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

    /*
    @Override
    public byte[] toBytes(CertifierLock transaction) {
        return transaction.bytes();
    }

    @Override
    public Try<CertifierLock> parseBytesTry(byte[] bytes) {
        return CertifierLock.parseBytes(bytes);
    }
    */

    @Override
    public void serialize(CertifierLock transaction, Writer writer) {
        writer.putBytes(transaction.bytes());
    }

    @Override
    public CertifierLock parse(Reader reader) {
        return CertifierLock.parseBytes(reader.getBytes(reader.remaining())).get();
    }
}
