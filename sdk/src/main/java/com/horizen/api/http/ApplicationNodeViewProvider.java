package com.horizen.api.http;

import com.horizen.node.SidechainNodeView;
import scala.util.Try;

public interface ApplicationNodeViewProvider {

    public Try<SidechainNodeView> getSidechainNodeView();

}
