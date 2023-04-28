package io.horizen.examples.transaction;

import io.horizen.proof.Signature25519;
import io.horizen.proposition.Proposition;
import io.horizen.transaction.TransactionSerializer;
import io.horizen.utxo.box.Box;
import io.horizen.utxo.box.data.BoxData;
import io.horizen.utxo.box.data.CrossChainRedeemMessageBoxData;
import io.horizen.utxo.box.data.CrossChainRedeemMessageBoxDataSerializer;
import io.horizen.utxo.box.data.ZenBoxData;
import io.horizen.utxo.transaction.AbstractCrossChainRedeemTransaction;
import sparkz.core.NodeViewModifier$;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RedeemVoteMessageTransaction extends AbstractCrossChainRedeemTransaction {
    public static final byte TX_VERSION = 1;
    private final byte version;
    List<BoxData<Proposition, Box<Proposition>>> customBoxesData;

    public RedeemVoteMessageTransaction(
            List<byte[]> inputZenBoxIds,
            List<Signature25519> inputZenBoxProofs,
            List<ZenBoxData> outputZenBoxesData,
            CrossChainRedeemMessageBoxData outputRedeemMsgBoxData,
            byte version,
            long fee
    ) {
        super(inputZenBoxIds, inputZenBoxProofs, outputZenBoxesData, fee, outputRedeemMsgBoxData);
        this.version = version;
    }

    @Override
    protected List<BoxData<Proposition, Box<Proposition>>> getCustomOutputData() {
        if (customBoxesData == null) {
            customBoxesData = new ArrayList<>();
            customBoxesData.add((BoxData) redeemMessageBox);
        }
        return Collections.unmodifiableList(customBoxesData);
    }

    void serialize(Writer writer) {
        writer.put(version);
        writer.putLong(fee);
        TransactionSerializerUtils.putBytesFixedList(inputZenBoxIds, writer);
        zenBoxProofsSerializer.serialize(inputZenBoxProofs, writer);
        zenBoxDataListSerializer.serialize(outputZenBoxesData, writer);
        CrossChainRedeemMessageBoxDataSerializer.getSerializer().serialize(redeemMessageBox, writer);
    }

    static RedeemVoteMessageTransaction parse(Reader reader) {
        byte version = reader.getByte();
        long fee = reader.getLong();

        List<byte[]> inputZenBoxIds = TransactionSerializerUtils.getBytesFixedList(NodeViewModifier$.MODULE$.ModifierIdSize(), reader);
        List<Signature25519> inputZenBoxProofs = zenBoxProofsSerializer.parse(reader);
        List<ZenBoxData> outputZenBoxesData = zenBoxDataListSerializer.parse(reader);

        CrossChainRedeemMessageBoxData outputBoxData = CrossChainRedeemMessageBoxDataSerializer.getSerializer().parse(reader);

        return new RedeemVoteMessageTransaction(
                inputZenBoxIds, inputZenBoxProofs, outputZenBoxesData, outputBoxData, version, fee
        );
    }

    @Override
    public byte transactionTypeId() {
        return 0;
    }

    @Override
    public byte version() {
        return version;
    }

    @Override
    public byte[] customFieldsData() {
        return new byte[0];
    }

    @Override
    public byte[] customDataMessageToSign() {
        return new byte[0];
    }

    @Override
    public TransactionSerializer serializer() {
        return RedeemVoteMessageTransactionSerializer.getSerializer();
    }
}
