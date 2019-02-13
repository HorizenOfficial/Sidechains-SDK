package com.horizen.transaction.mainchain;

import com.horizen.box.CertifierRightBox;
import com.horizen.utils.Utils;
import scala.util.Success;
import scala.util.Try;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CertifierLock implements SidechainRelatedMainchainTransaction<CertifierRightBox> {

    private byte[] _transactionBytes;

    public CertifierLock(byte[] transactionBytes) {
        _transactionBytes = Arrays.copyOf(transactionBytes, transactionBytes.length);
    }
    @Override
    public byte[] hash() {
        return Utils.doubleSHA256Hash(_transactionBytes);
    }

    // DO TO: parse outputs, detect SC related addresses (PublicKey25519Proposition) and values, create CertifierLockBoxes for them.
    @Override
    public List<CertifierRightBox> outputs() {
        return new ArrayList<>();
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(_transactionBytes, _transactionBytes.length);
    }

    public static Try<CertifierLock> parseBytes(byte[] bytes) {
        // do some checks
        return new Success<>(new CertifierLock(bytes));
    }

    @Override
    public SidechainRelatedMainchainTransactionSerializer serializer() {
        return CertifierLockSerializer.getSerializer();
    }
}
