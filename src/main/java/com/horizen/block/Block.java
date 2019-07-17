package com.horizen.block;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.BoxTransaction;

public interface Block<BT extends BoxTransaction<P, B>,P extends  Proposition,B extends Box<P>> extends scorex.core.block.Block<BT> {
}
