package io.horizen.utxo.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import io.horizen.SidechainTypes
import io.horizen.api.http.route.ApplicationBaseApiRoute
import io.horizen.utxo.api.http.SidechainApplicationApiGroup
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import io.horizen.utxo.chain.SidechainFeePaymentsInfo
import io.horizen.utxo.node._
import sparkz.core.settings.RESTApiSettings


case class SidechainApplicationApiRoute(override val settings: RESTApiSettings, applicationApiGroup: SidechainApplicationApiGroup, sidechainNodeViewHolderRef: ActorRef)
                                       (implicit override val context: ActorRefFactory)
  extends ApplicationBaseApiRoute[
    SidechainTypes#SCBT,
    SidechainBlockHeader,
    SidechainBlock,
    SidechainFeePaymentsInfo,
    NodeHistory,
    NodeState,
    NodeWallet,
    NodeMemoryPool,
    SidechainNodeView] (settings, applicationApiGroup, sidechainNodeViewHolderRef)
{


}
