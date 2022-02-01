package com.horizen.transaction.mainchain;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.block.MainchainTxSidechainCreationCrosschainOutput;
import com.horizen.box.ForgerBox;
import com.horizen.box.data.ForgerBoxData;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Utils;
import com.horizen.proposition.VrfPublicKey;
import scala.compat.java8.OptionConverters;
import scorex.crypto.hash.Blake2b256;
import java.util.Optional;

public final class SidechainCreation implements SidechainRelatedMainchainOutput<ForgerBox> {
    private MainchainTxSidechainCreationCrosschainOutput output;
    private byte[] containingTxHash;
    private int index;

    public SidechainCreation(MainchainTxSidechainCreationCrosschainOutput output, byte[] containingTxHash, int index) {
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
    public ForgerBox getBox() {
        // Note: SC output address is stored in original MC LE form, but we in SC we expect BE raw data.
        PublicKey25519Proposition proposition = new PublicKey25519Proposition(BytesUtils.reverseBytes(output.address()));
        long value = output.amount();
        VrfPublicKey vrfPublicKey = new VrfPublicKey(output.customCreationData());

        ForgerBoxData forgerBoxData = new ForgerBoxData(proposition, value, proposition, vrfPublicKey);

        byte[] hash = Blake2b256.hash(Bytes.concat(containingTxHash, Ints.toByteArray(index)));
        long nonce = BytesUtils.getLong(hash, 0);

        return forgerBoxData.getBox(nonce);
    }

    @Override
    public int transactionIndex() {
        return index;
    }

    public MainchainTxSidechainCreationCrosschainOutput getScCrOutput() {
        return output;
    }

    public Optional<byte[]> getGenSysConstantOpt() {
        return OptionConverters.toJava(output.constantOpt());
    }

    @Override
    public SidechainRelatedMainchainOutputSerializer serializer() {
        return SidechainCreationSerializer.getSerializer();
    }

    public int withdrawalEpochLength() {
        return output.withdrawalEpochLength();
    }
}
