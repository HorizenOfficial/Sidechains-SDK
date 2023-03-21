package io.horizen.account.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import io.horizen.SidechainTypes
import io.horizen.account.api.http.AccountApplicationApiGroup
import io.horizen.account.block.{AccountBlock, AccountBlockHeader}
import io.horizen.account.chain.AccountFeePaymentsInfo
import io.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import io.horizen.api.http.route.ApplicationBaseApiRoute
import io.horizen.node.NodeWalletBase
import sparkz.core.settings.RESTApiSettings


case class AccountApplicationApiRoute(override val settings: RESTApiSettings, applicationApiGroup: AccountApplicationApiGroup, sidechainNodeViewHolderRef: ActorRef)
                                       (implicit override val context: ActorRefFactory)
  extends ApplicationBaseApiRoute[
    SidechainTypes#SCAT,
    AccountBlockHeader,
    AccountBlock,
    AccountFeePaymentsInfo,
    NodeAccountHistory,
    NodeAccountState,
    NodeWalletBase,
    NodeAccountMemoryPool,
    AccountNodeView
  ] (settings, applicationApiGroup, sidechainNodeViewHolderRef) {



}
