package io.horizen.utxo.api.http;

import io.horizen.api.http.ApplicationBaseApiGroup;
import io.horizen.proposition.Proposition;
import io.horizen.utxo.block.SidechainBlock;
import io.horizen.utxo.block.SidechainBlockHeader;
import io.horizen.utxo.box.Box;
import io.horizen.utxo.chain.SidechainFeePaymentsInfo;
import io.horizen.utxo.node.*;
import io.horizen.utxo.transaction.BoxTransaction;

public abstract class SidechainApplicationApiGroup extends ApplicationBaseApiGroup<
        BoxTransaction<Proposition, Box<Proposition>>,
        SidechainBlockHeader,
        SidechainBlock,
        SidechainFeePaymentsInfo,
        NodeHistory,
        NodeState,
        NodeWallet,
        NodeMemoryPool,
        SidechainNodeView
        > {}