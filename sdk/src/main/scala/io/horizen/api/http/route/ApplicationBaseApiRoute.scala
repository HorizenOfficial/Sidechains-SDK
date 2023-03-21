package io.horizen.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import io.horizen.SidechainNodeViewBase
import io.horizen.api.http.{ApplicationBaseApiGroup, FunctionsApplierOnSidechainNodeView}
import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.chain.AbstractFeePaymentsInfo
import io.horizen.node.{NodeHistoryBase, NodeMemoryPoolBase, NodeStateBase, NodeWalletBase}
import io.horizen.transaction.Transaction
import sparkz.core.api.http.{ApiDirectives, ApiRoute}
import sparkz.core.settings.RESTApiSettings
import sparkz.util.SparkzEncoding


abstract class ApplicationBaseApiRoute[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  FPI <: AbstractFeePaymentsInfo,
  NH <: NodeHistoryBase[TX, H, PM, FPI],
  NS <: NodeStateBase,
  NW <: NodeWalletBase,
  NP <: NodeMemoryPoolBase[TX],
  NV <: SidechainNodeViewBase[TX, H, PM, FPI, NH, NS, NW, NP]](
                                  override val settings: RESTApiSettings,
                                  applicationApiGroup: ApplicationBaseApiGroup[TX, H, PM, FPI, NH, NS, NW, NP, NV],
                                  sidechainNodeViewHolderRef: ActorRef)
                                 (implicit val context: ActorRefFactory)
  extends ApiRoute
    with ApiDirectives
    with SparkzEncoding
    with FunctionsApplierOnSidechainNodeView[
    TX,
    H,
    PM,FPI, NH, NS, NW, NP, NV
  ] {
}
