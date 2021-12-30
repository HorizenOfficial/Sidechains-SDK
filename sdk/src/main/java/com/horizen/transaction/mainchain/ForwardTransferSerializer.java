package com.horizen.transaction.mainchain;

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
        if(reader.remaining() < 4 + ForwardTransfer.TRANSACTION_HASH_LENGTH + MainchainTxForwardTransferCrosschainOutput.FORWARD_TRANSFER_OUTPUT_SIZE())
            throw new IllegalArgumentException("Input data corrupted.");

        byte[] ftOutputBytes = reader.getBytes(MainchainTxForwardTransferCrosschainOutput.FORWARD_TRANSFER_OUTPUT_SIZE());
        MainchainTxForwardTransferCrosschainOutput output = MainchainTxForwardTransferCrosschainOutput.create(ftOutputBytes, 0).get();
        byte[] transactionHash = reader.getBytes(ForwardTransfer.TRANSACTION_HASH_LENGTH);
        int transactionIndex = reader.getInt();

        return new ForwardTransfer(output, transactionHash, transactionIndex);
    }
}
