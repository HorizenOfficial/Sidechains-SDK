package com.horizen.transaction.mainchain;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;

import java.util.List;
import java.util.Optional;

public interface SidechainRelatedMainchainOutput<B extends Box<? extends Proposition>> extends scorex.core.serialization.BytesSerializable
{
    byte[] hash();

    byte[] transactionHash();

    int transactionIndex();

    byte[] sidechainId();

    B getBox();

    @Override
    default byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    SidechainRelatedMainchainOutputSerializer serializer();
}
