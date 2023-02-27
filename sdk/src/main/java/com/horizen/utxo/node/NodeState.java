package com.horizen.utxo.node;

import com.horizen.node.NodeStateBase;
import com.horizen.utxo.state.SidechainStateReader;
import com.horizen.utils.WithdrawalEpochInfo;

public interface NodeState extends NodeStateBase, SidechainStateReader {

    WithdrawalEpochInfo getWithdrawalEpochInfo();
}
