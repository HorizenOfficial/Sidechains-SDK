package io.horizen.node;

import io.horizen.consensus.ForgingStakeInfo;

public interface NodeStateBase  {
    scala.collection.Seq<ForgingStakeInfo> getOrderedForgingStakesInfoSeq(int epochNumber);

    boolean hasCeased();

}
