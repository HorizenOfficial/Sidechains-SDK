package com.horizen.transaction.mainchain;

import com.horizen.params.CommonParams;
import com.horizen.block.MainchainTxForwardTransferCrosschainOutput;
import com.horizen.utils.Checker;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;


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
        int ftOutputLength = Checker.readIntNotLessThanZero(reader, )();
        byte[] ftOutputBytes = Checker.readBytes(reader, ftOutputLength, "ft output bytes");
        MainchainTxForwardTransferCrosschainOutput output = MainchainTxForwardTransferCrosschainOutput.create(ftOutputBytes, 0).get();
        byte[] transactionHash = Checker.readBytes(reader, CommonParams.mainchainTransactionHashLength(), "transaction hash");
        int transactionIndex = Checker.readIntNotLessThanZero(reader, )();

        return new ForwardTransfer(output, transactionHash, transactionIndex);
    }
}
