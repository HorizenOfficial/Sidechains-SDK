package com.horizen.transaction.mainchain;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;

import java.util.List;

public interface SidechainRelatedMainchainTransaction<B extends Box<? extends Proposition>> extends scorex.core.serialization.BytesSerializable
{
    byte[] hash();

    List<B> outputs();

    @Override
    byte[] bytes();

    @Override
    SidechainRelatedMainchainTransactionSerializer serializer();
}
