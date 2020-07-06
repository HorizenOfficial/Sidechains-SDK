package com.horizen.transaction.mainchain;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;

import java.util.List;
import java.util.Optional;

public interface SidechainRelatedMainchainOutput<B extends Box<? extends Proposition>> extends scorex.core.serialization.BytesSerializable
{
    byte[] hash();

    B getBox();

    @Override
    byte[] bytes();

    @Override
    SidechainRelatedMainchainOutputSerializer serializer();
}
