package com.horizen.transaction.mainchain;

import com.horizen.block.MainchainTransaction;
import com.horizen.box.CertifierRightBox;
import com.horizen.utils.Utils;
import scala.util.Success;
import scala.util.Try;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CertifierLock implements SidechainRelatedMainchainTransaction<CertifierRightBox> {

    private MainchainTransaction _mainchainTx;

    public CertifierLock(MainchainTransaction tx) {
        _mainchainTx = tx;
    }
    @Override
    public byte[] hash() {
        return _mainchainTx.hash();
    }

    // DO TO: loop through _mainchainTx.outputs, detect SC related addresses (PublicKey25519Proposition) and values, create CertifierLockBoxes for them.
    @Override
    public List<CertifierRightBox> outputs() {
        return new ArrayList<>();
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(_mainchainTx.bytes(), _mainchainTx.bytes().length);
    }

    public static Try<CertifierLock> parseBytes(byte[] bytes) {
        MainchainTransaction tx = new MainchainTransaction(bytes, 0);
        // TO DO: check if tx is a CertifierLock
        return new Success<>(new CertifierLock(tx));
    }

    @Override
    public SidechainRelatedMainchainTransactionSerializer serializer() {
        return CertifierLockSerializer.getSerializer();
    }

    public static boolean isCurrentSidechainCertifierLock(MainchainTransaction tx, byte[] sidechainId) {
        //TO DO: implement later, when CertifierLock structure in MC will be known
        return false;
    }
}
