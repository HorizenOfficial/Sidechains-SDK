package com.horizen.transaction.mainchain;

import com.horizen.block.MainchainTxForwardTransferOutput;
import com.horizen.block.MainchainTxForwardTransferOutputSerializer;
import com.horizen.box.RegularBox;
import scala.util.Success;
import scala.util.Try;

import java.util.Arrays;

public final class ForwardTransfer implements SidechainRelatedMainchainOutput<RegularBox> {

    private MainchainTxForwardTransferOutput _output;

    public ForwardTransfer(MainchainTxForwardTransferOutput output) {
        _output = output;
    }
    @Override
    public byte[] hash() {
        return _output.hash();
    }

    // DO TO: detect SC related addresses (PublicKey25519Proposition) and values, create RegularBoxes for them.
    @Override
    public RegularBox getBox() {
        return null;
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(_output.bytes(), _output.bytes().length);
    }

    public static Try<ForwardTransfer> parseBytes(byte[] bytes) {
        MainchainTxForwardTransferOutput output = MainchainTxForwardTransferOutputSerializer.parseBytes(bytes).get();
        return new Success<>(new ForwardTransfer(output));
    }

    @Override
    public SidechainRelatedMainchainOutputSerializer serializer() {
        return ForwardTransferSerializer.getSerializer();
    }
}
