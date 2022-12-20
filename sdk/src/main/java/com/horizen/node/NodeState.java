package com.horizen.node;

import com.horizen.params.NetworkParams;
import com.horizen.state.SidechainStateReader;
import com.horizen.utils.WithdrawalEpochInfo;

public interface NodeState extends NodeStateBase, SidechainStateReader {

    boolean hasCeased();

    NetworkParams params();

    WithdrawalEpochInfo getWithdrawalEpochInfo();
}
