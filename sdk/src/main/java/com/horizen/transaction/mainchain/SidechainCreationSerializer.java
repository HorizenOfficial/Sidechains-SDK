package com.horizen.transaction.mainchain;

import com.horizen.params.CommonParams;
import com.horizen.block.MainchainTxSidechainCreationCrosschainOutput;
import com.horizen.block.MainchainTxSidechainCreationCrosschainOutputData;
import com.horizen.utils.Checker;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public final class SidechainCreationSerializer implements SidechainRelatedMainchainOutputSerializer<SidechainCreation>
{
    private static SidechainCreationSerializer serializer;

    static {
        serializer = new SidechainCreationSerializer();
    }

    private SidechainCreationSerializer() {
        super();
    }

    public static SidechainCreationSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(SidechainCreation creationOutput, Writer writer) {
        byte[] scCreationOutputBytes = creationOutput.getScCrOutput().sidechainCreationOutputBytes();
        writer.putInt(scCreationOutputBytes.length);
        writer.putBytes(scCreationOutputBytes);
        writer.putBytes(creationOutput.transactionHash());
        writer.putInt(creationOutput.transactionIndex());
    }

    @Override
    public SidechainCreation parse(Reader reader) {
        int scCreationOutputLength = Checker.readIntNotLessThanZero(reader, "sidechain creation output length");
        byte[] scCreationOutputBytes = Checker.readBytes(reader, scCreationOutputLength, "sidechain creation output");
        MainchainTxSidechainCreationCrosschainOutputData scCreationOutputData = MainchainTxSidechainCreationCrosschainOutputData.create(scCreationOutputBytes, 0).get();
        byte[] transactionHash = Checker.readBytes(reader, CommonParams.mainchainTransactionHashLength(), "transaction hash");
        int transactionIndex = Checker.readIntNotLessThanZero(reader, "transaction index");
        byte[] sidechainId = MainchainTxSidechainCreationCrosschainOutput.calculateSidechainId(transactionHash, transactionIndex);
        return new SidechainCreation(new MainchainTxSidechainCreationCrosschainOutput(sidechainId, scCreationOutputData), transactionHash, transactionIndex);
    }
}
