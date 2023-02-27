package com.horizen.utxo.api.http;

import com.horizen.utxo.node.SidechainNodeView;
import scala.util.Try;

public interface ApplicationNodeViewProvider {

    public Try<SidechainNodeView> getSidechainNodeView();

}
