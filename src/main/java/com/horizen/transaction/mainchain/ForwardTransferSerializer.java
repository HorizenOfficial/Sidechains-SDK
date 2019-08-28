package com.horizen.transaction.mainchain;

import scala.util.Try;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class ForwardTransferSerializer implements SidechainRelatedMainchainOutputSerializer<ForwardTransfer>
{
    private static ForwardTransferSerializer serializer;

    static {
        serializer = new ForwardTransferSerializer();
    }

    private ForwardTransferSerializer() {
        super();
    }

    public static ForwardTransferSerializer getSerializer() {
        return serializer;
    }

    /*
    @Override
    public byte[] toBytes(ForwardTransfer transaction) {
        return transaction.bytes();
    }

    @Override
    public Try<ForwardTransfer> parseBytesTry(byte[] bytes) {
        return ForwardTransfer.parseBytes(bytes);
    }
    */

    @Override
    public void serialize(ForwardTransfer transaction, Writer writer) {
        writer.putBytes(transaction.bytes());
    }

    @Override
    public ForwardTransfer parse(Reader reader) {
        return ForwardTransfer.parseBytes(reader.getBytes(reader.remaining()));
    }
}
