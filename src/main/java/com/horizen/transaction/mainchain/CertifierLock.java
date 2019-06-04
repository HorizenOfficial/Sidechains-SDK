package com.horizen.transaction.mainchain;

import com.horizen.block.MainchainTxCertifierLockCrosschainOutput;
import com.horizen.box.CertifierRightBox;
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
    public CertifierRightBox getBox(long nonce) {
        return new CertifierRightBox(_output.proposition(), nonce, _output.amount(), _output.activeFromWithdrawalEpoch());
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
