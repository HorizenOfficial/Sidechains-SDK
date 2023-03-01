package io.horizen.utxo.node;

import io.horizen.node.NodeStateBase;
import io.horizen.utxo.state.SidechainStateReader;
import io.horizen.utils.WithdrawalEpochInfo;

public interface NodeState extends NodeStateBase, SidechainStateReader {

    WithdrawalEpochInfo getWithdrawalEpochInfo();
}
