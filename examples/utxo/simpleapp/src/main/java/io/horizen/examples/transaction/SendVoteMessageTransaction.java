package io.horizen.examples.transaction;

import io.horizen.proof.Signature25519;
import io.horizen.proposition.Proposition;
import io.horizen.transaction.TransactionSerializer;
import io.horizen.transaction.exception.TransactionSemanticValidityException;
import io.horizen.utxo.box.Box;
import io.horizen.utxo.box.data.BoxData;
import io.horizen.utxo.box.data.CrossChainMessageBoxData;
import io.horizen.utxo.box.data.CrossChainMessageBoxDataSerializer;
import io.horizen.utxo.box.data.ZenBoxData;
import io.horizen.utxo.transaction.AbstractRegularTransaction;
import sparkz.core.NodeViewModifier$;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.horizen.examples.transaction.TransactionIdsEnum.SendVoteToSidechainTransactionId;

public final class SendVoteMessageTransaction extends AbstractRegularTransaction {
    public static final byte TX_VERSION = 1;
    private final CrossChainMessageBoxData outputMsgBoxData;
    private final byte version;
    List<BoxData<Proposition, Box<Proposition>>> customBoxesData;

    public SendVoteMessageTransaction(
            List<byte[]> inputZenBoxIds,
            List<Signature25519> inputZenBoxProofs,
            List<ZenBoxData> outputZenBoxesData,
            CrossChainMessageBoxData outputMsgBoxData,
            byte version,
            long fee
    ) {
        super(inputZenBoxIds, inputZenBoxProofs, outputZenBoxesData, fee);
        this.outputMsgBoxData = outputMsgBoxData;
        this.version = version;
    }

    @Override
    public TransactionSerializer serializer() {
        return SendVoteMessageTransactionSerializer.getSerializer();
    }

    @Override
    protected List<BoxData<Proposition, Box<Proposition>>> getCustomOutputData() {
        if (customBoxesData == null) {
            customBoxesData = new ArrayList<>();
            customBoxesData.add((BoxData) outputMsgBoxData);
        }
        return Collections.unmodifiableList(customBoxesData);
    }

    void serialize(Writer writer) {
        writer.put(version);
        writer.putLong(fee);
        TransactionSerializerUtils.putBytesFixedList(inputZenBoxIds, writer);
        zenBoxProofsSerializer.serialize(inputZenBoxProofs, writer);
        zenBoxDataListSerializer.serialize(outputZenBoxesData, writer);
        CrossChainMessageBoxDataSerializer.getSerializer().serialize(outputMsgBoxData, writer);
    }

    static SendVoteMessageTransaction parse(Reader reader) {
        byte version = reader.getByte();
        long fee = reader.getLong();

        List<byte[]> inputZenBoxIds = TransactionSerializerUtils.getBytesFixedList(NodeViewModifier$.MODULE$.ModifierIdSize(), reader);
        List<Signature25519> inputZenBoxProofs = zenBoxProofsSerializer.parse(reader);
        List<ZenBoxData> outputZenBoxesData = zenBoxDataListSerializer.parse(reader);

        CrossChainMessageBoxData outputBoxData = CrossChainMessageBoxDataSerializer.getSerializer().parse(reader);

        return new SendVoteMessageTransaction(
                inputZenBoxIds, inputZenBoxProofs, outputZenBoxesData, outputBoxData, version, fee
        );
    }

    @Override
    public byte transactionTypeId() {
        return SendVoteToSidechainTransactionId.id();
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
}
