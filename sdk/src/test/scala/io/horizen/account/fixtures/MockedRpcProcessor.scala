package io.horizen.account.fixtures

import akka.actor.ActorRef
import io.horizen.SidechainSettings
import io.horizen.account.api.rpc.handler.RpcHandler
import io.horizen.account.api.rpc.service.{EthService, RpcProcessor, RpcUtils}
import io.horizen.account.companion.SidechainAccountTransactionsCompanion
import io.horizen.fixtures.SidechainBlockFixture.getDefaultAccountTransactionsCompanion
import io.horizen.params.{MainNetParams, NetworkParams}

import scala.concurrent.duration._


case class MockedRpcProcessor(mockedSidechainNodeViewHolderRef: ActorRef,
                         mockedNetworkControllerRef: ActorRef,
                         timeout: FiniteDuration = 1 seconds,
                         params: NetworkParams = MainNetParams(),
                         mockedSidechainSettings: SidechainSettings,
                         mockedSidechainTransactionActorRef: ActorRef,
                         mockedSyncStatusActorRef: ActorRef,
                         sidechainTransactionsCompanion: SidechainAccountTransactionsCompanion = getDefaultAccountTransactionsCompanion
                        ) {

  val rpcHandler = new RpcHandler(
    new EthService(
      mockedSidechainNodeViewHolderRef,
      mockedNetworkControllerRef,
      timeout,
      params,
      mockedSidechainSettings.ethService,
      mockedSidechainSettings.sparkzSettings.network.maxIncomingConnections,
      RpcUtils.getClientVersion("dev"),
      mockedSidechainTransactionActorRef,
      mockedSyncStatusActorRef,
      sidechainTransactionsCompanion
    )
  )
  val rpcProcessor: RpcProcessor = RpcProcessor(rpcHandler)
}
