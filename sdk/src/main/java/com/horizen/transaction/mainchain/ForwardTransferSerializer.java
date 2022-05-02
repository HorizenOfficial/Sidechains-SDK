package com.horizen.transaction.mainchain;

import com.horizen.CommonParams;
import com.horizen.block.MainchainTxForwardTransferCrosschainOutput;
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

    @Override
    public void serialize(ForwardTransfer forwardTransferOutput, Writer writer) {
        byte[] ftOutputBytes = forwardTransferOutput.getFtOutput().forwardTransferOutputBytes();
        writer.putInt(ftOutputBytes.length);
        writer.putBytes(ftOutputBytes);
        writer.putBytes(forwardTransferOutput.transactionHash());
        writer.putInt(forwardTransferOutput.transactionIndex());
    }

    @Override
    public ForwardTransfer parse(Reader reader) {
        int ftOutputLength = reader.getInt();;
        byte[] ftOutputBytes = reader.getBytes(ftOutputLength);
        MainchainTxForwardTransferCrosschainOutput output = MainchainTxForwardTransferCrosschainOutput.create(ftOutputBytes, 0).get();
        byte[] transactionHash = reader.getBytes(CommonParams.mainchainTransactionHashLength());
        int transactionIndex = reader.getInt();

        return new ForwardTransfer(output, transactionHash, transactionIndex);
    }
}
