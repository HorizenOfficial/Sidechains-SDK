package com.horizen.transaction.mainchain;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.block.MainchainTxForwardTransferCrosschainOutput;
import com.horizen.box.ZenBox;
import com.horizen.box.data.ZenBoxData;
import com.horizen.cryptolibprovider.FieldElementUtils;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Utils;
import scorex.crypto.hash.Blake2b256;


public final class ForwardTransfer implements SidechainRelatedMainchainOutput<ZenBox> {
    private MainchainTxForwardTransferCrosschainOutput output;
    private byte[] containingTxHash;
    private int index;

    public ForwardTransfer(MainchainTxForwardTransferCrosschainOutput output, byte[] containingTxHash, int index) {
        this.output = output;
        this.containingTxHash = containingTxHash;
        this.index = index;
    }

    @Override
    public byte[] hash() {
        return BytesUtils.reverseBytes(Utils.doubleSHA256Hash(Bytes.concat(
                output.hash(),
                containingTxHash,
                BytesUtils.reverseBytes(Ints.toByteArray(index))
        )));
    }

    @Override
    public byte[] transactionHash() {
        return containingTxHash;
    }

    @Override
    public int transactionIndex() {
        return index;
    }

    @Override
    public byte[] sidechainId() {
        return output.sidechainId();
    }

    @Override
    public ZenBox getBox() {
        byte[] hash = Blake2b256.hash(Bytes.concat(containingTxHash, Ints.toByteArray(index)));
        long nonce = BytesUtils.getLong(hash, 0);
        return new ZenBox(
                new ZenBoxData(
                        // Note: SC output address is stored in original MC LE form, but we in SC we expect BE raw data.
                        new PublicKey25519Proposition(BytesUtils.reverseBytes(output.propositionBytes())),
                        output.amount()),
                nonce);
    }

    public MainchainTxForwardTransferCrosschainOutput getFtOutput() {
        return output;
    }

    @Override
    public SidechainRelatedMainchainOutputSerializer serializer() {
        return ForwardTransferSerializer.getSerializer();
    }

    @Override
    public String toString() {
        return String.format("ForwardTransfer {\ntxHash = %s\nindex = %d\nftoutput = %s\n}",
                BytesUtils.toHexString(containingTxHash), index, output);
    }
}
