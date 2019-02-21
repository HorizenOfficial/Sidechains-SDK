package com.horizen.transaction.mainchain;

import scala.util.Try;

public final class ForwardTransferSerializer implements SidechainRelatedMainchainTransactionSerializer<ForwardTransfer>
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

    @Override
    public byte[] toBytes(ForwardTransfer transaction) {
        return transaction.bytes();
    }

    @Override
    public Try<ForwardTransfer> parseBytes(byte[] bytes) {
        return ForwardTransfer.parseBytes(bytes);
    }
}
