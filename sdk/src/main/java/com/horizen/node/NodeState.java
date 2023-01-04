package com.horizen.node;

import com.horizen.state.SidechainStateReader;
import com.horizen.utils.WithdrawalEpochInfo;

public interface NodeState extends NodeStateBase, SidechainStateReader {

    WithdrawalEpochInfo getWithdrawalEpochInfo();
}
