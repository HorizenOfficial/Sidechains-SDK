package com.horizen.node;

import com.horizen.state.SidechainStateReader;

public interface NodeState extends NodeStateBase, SidechainStateReader {

    boolean hasCeased();
}
