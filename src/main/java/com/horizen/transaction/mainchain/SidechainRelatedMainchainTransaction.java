package com.horizen.transaction.mainchain;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;

import java.util.List;

public interface SidechainRelatedMainchainTransaction<P extends Proposition, B extends Box<P>> extends scorex.core.serialization.BytesSerializable
{
    byte[] hash();

    List<B> outputs();

    @Override
    byte[] bytes();

    @Override
    SidechainRelatedMainchainTransactionSerializer serializer();
}
