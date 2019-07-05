package com.horizen.transaction.mainchain;

import com.horizen.block.MainchainTxCertifierLockCrosschainOutput;
import com.horizen.box.CertifierRightBox;
import com.horizen.proposition.PublicKey25519Proposition;
import scala.util.Success;
import scala.util.Try;

import java.util.Arrays;

public final class CertifierLock implements SidechainRelatedMainchainOutput<CertifierRightBox> {

    private MainchainTxCertifierLockCrosschainOutput _output;

    public CertifierLock(MainchainTxCertifierLockCrosschainOutput output) {
        _output = output;
    }
    @Override
    public byte[] hash() {
        return _output.hash();
    }

    @Override
    public CertifierRightBox getBox() {
        return new CertifierRightBox(new PublicKey25519Proposition(_output.propositionBytes()),  _output.nonce(), _output.amount(), _output.activeFromWithdrawalEpoch());
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(_output.certifierLockOutputBytes(), _output.certifierLockOutputBytes().length);
    }

    public static Try<CertifierLock> parseBytes(byte[] bytes) {
        MainchainTxCertifierLockCrosschainOutput output = MainchainTxCertifierLockCrosschainOutput.create(bytes, 0).get();
        return new Success<>(new CertifierLock(output));
    }

    @Override
    public SidechainRelatedMainchainOutputSerializer serializer() {
        return ForwardTransferSerializer.getSerializer();
    }
}
