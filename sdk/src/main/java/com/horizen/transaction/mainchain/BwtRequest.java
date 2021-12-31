package com.horizen.transaction.mainchain;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.block.MainchainTxBwtRequestCrosschainOutput;
import com.horizen.box.WithdrawalRequestBox;
import com.horizen.cryptolibprovider.FieldElementUtils;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Utils;

public final class BwtRequest implements SidechainRelatedMainchainOutput<WithdrawalRequestBox> {
    private final MainchainTxBwtRequestCrosschainOutput output;
    private final byte[] containingTxHash;
    private final int index;

    public BwtRequest(MainchainTxBwtRequestCrosschainOutput output, byte[] containingTxHash, int index) {
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
    public byte[] sidechainId() {
        return output.sidechainId();
    }

    @Override
    public WithdrawalRequestBox getBox() {
        throw new UnsupportedOperationException("There is no support of BwtRequest processing at the moment.");
    }

    @Override
    public int transactionIndex() {
        return index;
    }

    public MainchainTxBwtRequestCrosschainOutput getBwtOutput() {
        return output;
    }

    @Override
    public SidechainRelatedMainchainOutputSerializer serializer() {
        return BwtRequestSerializer.getSerializer();
    }
}
