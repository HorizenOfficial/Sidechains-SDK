package com.horizen.node;

import com.horizen.consensus.ForgingStakeInfo;

public interface NodeStateBase  {
    scala.collection.Seq<ForgingStakeInfo> getOrderedForgingStakesInfoSeq();

    boolean hasCeased();

}
