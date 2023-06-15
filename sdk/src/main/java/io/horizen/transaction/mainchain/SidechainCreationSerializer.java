package io.horizen.transaction.mainchain;

import io.horizen.block.MainchainTxSidechainCreationCrosschainOutput;
import io.horizen.block.MainchainTxSidechainCreationCrosschainOutputData;
import io.horizen.params.CommonParams;
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
        int scCreationOutputLength = reader.getInt();
        byte[] scCreationOutputBytes = reader.getBytes(scCreationOutputLength);
        MainchainTxSidechainCreationCrosschainOutputData scCreationOutputData = MainchainTxSidechainCreationCrosschainOutputData.create(scCreationOutputBytes, 0).get();
        byte[] transactionHash = reader.getBytes(CommonParams.mainchainTransactionHashLength());
        int transactionIndex = reader.getInt();
        byte[] sidechainId = MainchainTxSidechainCreationCrosschainOutput.calculateSidechainId(transactionHash, transactionIndex);

        return new SidechainCreation(new MainchainTxSidechainCreationCrosschainOutput(sidechainId, scCreationOutputData), transactionHash, transactionIndex);
    }
}
