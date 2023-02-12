package com.horizen.transaction.mainchain;

import com.horizen.params.CommonParams;
import com.horizen.block.MainchainTxBwtRequestCrosschainOutput;
import com.horizen.utils.Checker;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

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
        int bwtOutputLength = Checker.readIntNotLessThanZero(reader, )();
        byte[] bwtOutputBytes = Checker.readBytes(reader, bwtOutputLength, "backward transfer output");
        MainchainTxBwtRequestCrosschainOutput bwtOutput = MainchainTxBwtRequestCrosschainOutput.create(bwtOutputBytes, 0).get();
        byte[] transactionHash = Checker.readBytes(reader, CommonParams.mainchainTransactionHashLength(), "transaction hash");
        int transactionIndex = Checker.readIntNotLessThanZero(reader, )();

        return new BwtRequest(bwtOutput, transactionHash, transactionIndex);
    }
}