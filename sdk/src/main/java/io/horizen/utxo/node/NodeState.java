package io.horizen.utxo.node;

import io.horizen.node.NodeStateBase;
import io.horizen.utils.WithdrawalEpochInfo;
import io.horizen.utxo.state.SidechainStateReader;

public interface NodeState extends NodeStateBase, SidechainStateReader {

    WithdrawalEpochInfo getWithdrawalEpochInfo();
}
