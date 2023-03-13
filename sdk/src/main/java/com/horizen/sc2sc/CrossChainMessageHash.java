package com.horizen.sc2sc;

import sparkz.core.serialization.BytesSerializable;

public interface CrossChainMessageHash  extends BytesSerializable {
    byte[] getValue();
}
