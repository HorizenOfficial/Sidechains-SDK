package com.horizen.api;

import akka.http.javadsl.server.Route;
import akka.http.scaladsl.server.RequestContext;
import akka.http.scaladsl.server.RouteConcatenation$;
import akka.http.scaladsl.server.RouteResult;
import com.horizen.api.http.SidechainApiRouteWithFullView;
import com.horizen.node.NodeHistory;
import com.horizen.node.NodeMemoryPool;
import com.horizen.node.NodeState;
import com.horizen.node.NodeWallet;
import scala.Function1;
import scala.concurrent.Future;

public abstract class SidechainApiCallContainerWithFullView
        <H extends NodeHistory, S extends NodeState, W extends NodeWallet, M extends NodeMemoryPool>
        extends SidechainApiRouteWithFullView<H, S, W, M> implements ApiCallContainer {


}
