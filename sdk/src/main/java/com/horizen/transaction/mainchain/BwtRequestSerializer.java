package com.horizen.transaction.mainchain;

import com.horizen.CommonParams;
import com.horizen.block.MainchainTxBwtRequestCrosschainOutput;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class BwtRequestSerializer implements SidechainRelatedMainchainOutputSerializer<BwtRequest>
{
    private static BwtRequestSerializer serializer;

    static {
        serializer = new BwtRequestSerializer();
    }

    private BwtRequestSerializer() {
        super();
    }

    public static BwtRequestSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(BwtRequest bwtRequestOutput, Writer writer) {
        byte[] bwtOutputBytes = bwtRequestOutput.getBwtOutput().bwtRequestOutputBytes();
        writer.putInt(bwtOutputBytes.length);
        writer.putBytes(bwtOutputBytes);
        writer.putBytes(bwtRequestOutput.transactionHash());
        writer.putInt(bwtRequestOutput.transactionIndex());
    }

    @Override
    public BwtRequest parse(Reader reader) {
        int bwtOutputLength = reader.getInt();
        byte[] bwtOutputBytes = reader.getBytes(bwtOutputLength);
        MainchainTxBwtRequestCrosschainOutput bwtOutput = MainchainTxBwtRequestCrosschainOutput.create(bwtOutputBytes, 0).get();
        byte[] transactionHash = reader.getBytes(CommonParams.mainchainTransactionHashLength());
        int transactionIndex = reader.getInt();

        return new BwtRequest(bwtOutput, transactionHash, transactionIndex);
    }
}