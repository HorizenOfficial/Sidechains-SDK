package io.horizen.sc2sc;

import sparkz.core.serialization.BytesSerializable;

public interface CrossChainMessageHash  extends BytesSerializable {

    public byte[] getValue();
}
