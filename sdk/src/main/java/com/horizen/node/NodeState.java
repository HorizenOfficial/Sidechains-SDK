package com.horizen.node;

import com.horizen.state.SidechainStateReader;

public interface NodeState extends SidechainStateReader {

    boolean hasCeased();
}
