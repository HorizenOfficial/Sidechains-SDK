package com.horizen.transaction.mainchain;

import com.horizen.block.MainchainTransaction;
import com.horizen.box.RegularBox;
import com.horizen.utils.Utils;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ForwardTransfer implements SidechainRelatedMainchainTransaction<RegularBox> {

    private MainchainTransaction _mainchainTx;

    public ForwardTransfer(MainchainTransaction tx) {
        _mainchainTx = tx;
    }
    @Override
    public byte[] hash() {
        return _mainchainTx.hash();
    }

    // DO TO: loop through _mainchainTx.outputs, detect SC related addresses (PublicKey25519Proposition) and values, create RegularBoxes for them.
    @Override
    public List<RegularBox> outputs() {
        return new ArrayList<>();
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(_mainchainTx.bytes(), _mainchainTx.bytes().length);
    }

    public static Try<ForwardTransfer> parseBytes(byte[] bytes) {
        MainchainTransaction tx = new MainchainTransaction(bytes, 0);
        // TO DO: check if tx is a ForwardTransfer
        return new Success<>(new ForwardTransfer(tx));
    }

    @Override
    public SidechainRelatedMainchainTransactionSerializer serializer() {
        return ForwardTransferSerializer.getSerializer();
    }
}
