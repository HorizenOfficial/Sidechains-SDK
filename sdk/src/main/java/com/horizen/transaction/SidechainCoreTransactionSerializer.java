package com.horizen.transaction;


import com.horizen.companion.SidechainBoxesDataCompanion;
import com.horizen.companion.SidechainProofsCompanion;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class SidechainCoreTransactionSerializer implements TransactionSerializer<SidechainCoreTransaction>
{
    private SidechainBoxesDataCompanion boxesDataCompanion;
    private SidechainProofsCompanion proofsCompanion;

    public SidechainCoreTransactionSerializer(SidechainBoxesDataCompanion boxesDataCompanion, SidechainProofsCompanion proofsCompanion) {
        this.boxesDataCompanion = boxesDataCompanion;
        this.proofsCompanion = proofsCompanion;
    }

    @Override
    public void serialize(SidechainCoreTransaction transaction, Writer writer) {
        writer.putBytes(transaction.bytes());
    }

    @Override
    public SidechainCoreTransaction parse(Reader reader) {
        return SidechainCoreTransaction.parseBytes(reader.getBytes(reader.remaining()), boxesDataCompanion, proofsCompanion);
    }
}
