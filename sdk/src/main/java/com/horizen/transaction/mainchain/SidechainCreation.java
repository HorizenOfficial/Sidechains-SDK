package com.horizen.transaction.mainchain;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.block.MainchainTxSidechainCreationCrosschainOutput;
import com.horizen.box.ForgerBox;
import com.horizen.box.data.ForgerBoxData;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Ed25519;
import com.horizen.utils.Utils;
import com.horizen.utils.Pair;
import com.horizen.secret.VrfKeyGenerator;
import com.horizen.proposition.VrfPublicKey;
import com.horizen.secret.VrfSecretKey;

import java.util.Arrays;
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
                BytesUtils.reverseBytes(output.hash()),
                BytesUtils.reverseBytes(containingTxHash),
                BytesUtils.reverseBytes(Ints.toByteArray(index))
        )));
    }

    @Override
    public Optional<ForgerBox> getBox() {
        // at the moment sc creation output doesn't create any new coins.
        return Optional.of(getHardcodedGenesisForgerBox());
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(
                Ints.toByteArray(output.size()),
                output.sidechainCreationOutputBytes(),
                containingTxHash,
                Ints.toByteArray(index)
        );
    }

    public byte[] getBackwardTransferPoseidonRootHash() {
        return new byte[0];
    }

    public static SidechainCreation parseBytes(byte[] bytes) {

        int offset = 0;

        int sidechainCreationSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        if(bytes.length < 40 + sidechainCreationSize)
            throw new IllegalArgumentException("Input data corrupted.");

        MainchainTxSidechainCreationCrosschainOutput output = MainchainTxSidechainCreationCrosschainOutput.create(bytes, offset).get();
        offset += sidechainCreationSize;

        byte[] txHash = Arrays.copyOfRange(bytes, offset, offset + 32);
        offset += 32;

        int idx = BytesUtils.getInt(bytes, offset);
        return new SidechainCreation(output, txHash, idx);
    }

    @Override
    public SidechainRelatedMainchainOutputSerializer serializer() {
        return SidechainCreationSerializer.getSerializer();
    }

    public int withdrawalEpochLength() {
        return output.withdrawalEpochLength();
    }



    private static Pair<byte[], byte[]> genesisStakeKeys = Ed25519.createKeyPair("ThatForgerBoxShallBeGetFromGenesisBoxNotHardcoded".getBytes());
    public static PrivateKey25519 genesisSecret = new PrivateKey25519(genesisStakeKeys.getKey(), genesisStakeKeys.getValue());
    public static VrfSecretKey vrfGenesisSecretKey = VrfKeyGenerator.getInstance().generateSecret(genesisStakeKeys.getKey());
    private static VrfPublicKey vrfPublicKey = vrfGenesisSecretKey.publicImage();

    public static long initialValue = 10000000000L;
    public static ForgerBox getHardcodedGenesisForgerBox() {
        PublicKey25519Proposition proposition = genesisSecret.publicImage();
        PublicKey25519Proposition rewardProposition = genesisSecret.publicImage();
        ForgerBoxData forgerBoxData = new ForgerBoxData(proposition, initialValue, rewardProposition, vrfPublicKey);
        long nonce = 42L;

        return forgerBoxData.getBox(nonce);
    }

}
