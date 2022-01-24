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
        writer.putBytes(forwardTransferOutput.getFtOutput().forwardTransferOutputBytes());
        writer.putBytes(forwardTransferOutput.transactionHash());
        writer.putInt(forwardTransferOutput.transactionIndex());
    }

    @Override
    public ForwardTransfer parse(Reader reader) {
        // Serialized transactionIndex can have length from 1 up to 4 bytes.
        if(reader.remaining() < 1 + CommonParams.transactionHashLength() + MainchainTxForwardTransferCrosschainOutput.FORWARD_TRANSFER_OUTPUT_SIZE())
            throw new IllegalArgumentException("Input data corrupted.");

        byte[] ftOutputBytes = reader.getBytes(MainchainTxForwardTransferCrosschainOutput.FORWARD_TRANSFER_OUTPUT_SIZE());
        MainchainTxForwardTransferCrosschainOutput output = MainchainTxForwardTransferCrosschainOutput.create(ftOutputBytes, 0).get();
        byte[] transactionHash = reader.getBytes(CommonParams.transactionHashLength());
        int transactionIndex = reader.getInt();

        return new ForwardTransfer(output, transactionHash, transactionIndex);
    }
}
