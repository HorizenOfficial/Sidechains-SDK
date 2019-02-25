package com.horizen.transaction.mainchain;

import com.horizen.block.MainchainTxCertifierLockOutput;
import com.horizen.block.MainchainTxCertifierLockOutputSeializer;
import com.horizen.box.CertifierRightBox;
import scala.util.Success;
import scala.util.Try;

import java.util.Arrays;

public final class CertifierLock implements SidechainRelatedMainchainOutput<CertifierRightBox> {

    private MainchainTxCertifierLockOutput _output;

    public CertifierLock(MainchainTxCertifierLockOutput output) {
        _output = output;
    }
    @Override
    public byte[] hash() {
        return _output.hash();
    }

    // DO TO: detect SC related addresses (PublicKey25519Proposition) and values, create CertifierRightBox for them.
    @Override
    public CertifierRightBox getBox() {
        return null;
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(_output.bytes(), _output.bytes().length);
    }

    public static Try<CertifierLock> parseBytes(byte[] bytes) {
        MainchainTxCertifierLockOutput output = MainchainTxCertifierLockOutputSeializer.parseBytes(bytes).get();
        return new Success<>(new CertifierLock(output));
    }

    @Override
    public SidechainRelatedMainchainOutputSerializer serializer() {
        return ForwardTransferSerializer.getSerializer();
    }
}
