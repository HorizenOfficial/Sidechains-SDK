package io.horizen.api.http;


import io.horizen.SidechainNodeViewBase;
import io.horizen.proposition.Proposition;
import io.horizen.utxo.block.SidechainBlock;
import io.horizen.utxo.block.SidechainBlockHeader;
import io.horizen.utxo.box.Box;
import io.horizen.utxo.chain.SidechainFeePaymentsInfo;
import io.horizen.utxo.node.*;
import io.horizen.utxo.transaction.BoxTransaction;

import java.util.Optional;
import java.util.function.BiFunction;


public abstract class ApplicationApiGroup extends ApplicationBaseApiGroup<
        BoxTransaction<Proposition, Box<Proposition>>,
        SidechainBlockHeader,
        SidechainBlock,
        SidechainFeePaymentsInfo,
        NodeHistory,
        NodeState,
        NodeWallet,
        NodeMemoryPool,
        SidechainNodeView> {



}