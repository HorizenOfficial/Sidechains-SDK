package com.horizen.transaction.mainchain;

import com.horizen.box.RegularBox;
import com.horizen.utils.Utils;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ForwardTransfer implements SidechainRelatedMainchainTransaction<RegularBox> {

    private byte[] _transactionBytes;
    public ForwardTransfer(byte[] transactionBytes) {
        _transactionBytes = Arrays.copyOf(transactionBytes, transactionBytes.length);
    }
    @Override
    public byte[] hash() {
        return Utils.doubleSHA256Hash(_transactionBytes);
    }

    // DO TO: parse outputs, detect SC related addresses (PublicKey25519Proposition) and values, create RegularBoxes for them.
    @Override
    public List<RegularBox> outputs() {
        return new ArrayList<>();
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(_transactionBytes, _transactionBytes.length);
    }

    public static Try<ForwardTransfer> parseBytes(byte[] bytes) {
        // do some checks
        return new Success<>(new ForwardTransfer(bytes));
    }

    @Override
    public SidechainRelatedMainchainTransactionSerializer serializer() {
        return ForwardTransferSerializer.getSerializer();
    }
}
