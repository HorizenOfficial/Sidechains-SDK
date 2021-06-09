package com.horizen.transaction.mainchain;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.block.MainchainTxBwtRequestCrosschainOutput;
import com.horizen.box.WithdrawalRequestBox;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Utils;

import java.util.Arrays;

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
    public byte[] bytes() {
        return Bytes.concat(
                output.bwtRequestOutputBytes(),
                containingTxHash,
                Ints.toByteArray(index)
        );
    }

    @Override
    public int transactionIndex() {
        return index;
    }

    public MainchainTxBwtRequestCrosschainOutput getBwtOutput() {
        return output;
    }

    public static BwtRequest parseBytes(byte[] bytes) {
        int offset = 0;

        MainchainTxBwtRequestCrosschainOutput output = MainchainTxBwtRequestCrosschainOutput.create(bytes, offset).get();
        offset += output.size();

        byte[] txHash = Arrays.copyOfRange(bytes, offset, offset + 32);
        offset += 32;

        int idx = BytesUtils.getInt(bytes, offset);

        return new BwtRequest(output, txHash, idx);
    }

    @Override
    public SidechainRelatedMainchainOutputSerializer serializer() {
        return BwtRequestSerializer.getSerializer();
    }
}
