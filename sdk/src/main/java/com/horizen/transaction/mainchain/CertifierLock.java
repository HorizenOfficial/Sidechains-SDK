package com.horizen.transaction.mainchain;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.block.MainchainTxCertifierLockCrosschainOutput;
import com.horizen.block.MainchainTxForwardTransferCrosschainOutput;
import com.horizen.box.CertifierRightBox;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Utils;
import scorex.crypto.hash.Blake2b256;

import java.util.Arrays;
import java.util.Optional;

public final class CertifierLock implements SidechainRelatedMainchainOutput<CertifierRightBox> {

    private MainchainTxCertifierLockCrosschainOutput output;
    private byte[] containingTxHash;
    private int index;

    public CertifierLock(MainchainTxCertifierLockCrosschainOutput output, byte[] containingTxHash, int index) {
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
    public Optional<CertifierRightBox> getBox() {
        byte[] hash = Blake2b256.hash(Bytes.concat(containingTxHash, Ints.toByteArray(index)));
        long nonce = BytesUtils.getLong(hash, 0);
        return Optional.of(new CertifierRightBox(new PublicKey25519Proposition(output.propositionBytes()), nonce, output.lockedAmount(), output.activeFromWithdrawalEpoch()));
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(
                output.certifierLockOutputBytes(),
                containingTxHash,
                Ints.toByteArray(index)
        );
    }

    public static CertifierLock parseBytes(byte[] bytes) {
        if(bytes.length < 36 + MainchainTxCertifierLockCrosschainOutput.CERTIFIER_LOCK_OUTPUT_SIZE())
            throw new IllegalArgumentException("Input data corrupted.");

        int offset = 0;

        MainchainTxCertifierLockCrosschainOutput output = MainchainTxCertifierLockCrosschainOutput.create(bytes, offset).get();
        offset += MainchainTxCertifierLockCrosschainOutput.CERTIFIER_LOCK_OUTPUT_SIZE();

        byte[] txHash = Arrays.copyOfRange(bytes, offset, offset + 32);
        offset += 32;

        int idx = BytesUtils.getInt(bytes, offset);

        return new CertifierLock(output, txHash, idx);
    }

    @Override
    public SidechainRelatedMainchainOutputSerializer serializer() {
        return CertifierLockSerializer.getSerializer();
    }
}
