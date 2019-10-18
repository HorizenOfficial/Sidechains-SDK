package com.horizen.transaction.mainchain;

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

    @Override
    public void serialize(CertifierLock certifierLockOutput, Writer writer) {
        writer.putBytes(certifierLockOutput.bytes());
    }

    @Override
    public CertifierLock parse(Reader reader) {
        return CertifierLock.parseBytes(reader.getBytes(reader.remaining()));
    }
}
