package com.horizen.api.http

import com.horizen.node.{NodeHistory, NodeMemoryPool, NodeState, NodeWallet, SidechainNodeView}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.mockito.MockitoSugar

class SidechainNodeViewUtilMocks extends MockitoSugar {

  def getNodeHistoryMock() : NodeHistory = {
    val history : NodeHistory = mock[NodeHistory]
    history
  }

  def getNodeStateMock() : NodeState = {
    val nodeState : NodeState = mock[NodeState]
    nodeState
  }

  def getNodeWalletMock() : NodeWallet = {
    val wallet : NodeWallet = mock[NodeWallet]
    Mockito.when(wallet.boxesBalance(ArgumentMatchers.any())).thenAnswer(_ => 1000)
    Mockito.when(wallet.allBoxesBalance).thenAnswer(_ => 5500)
    wallet
  }

  def getNodeMemoryPoolMock() : NodeMemoryPool = {
    val memoryPool : NodeMemoryPool = mock[NodeMemoryPool]
    memoryPool
  }

  def getSidechainNodeView() : SidechainNodeView = new SidechainNodeView(
    getNodeHistoryMock(),
    getNodeStateMock(),
    getNodeWalletMock(),
    getNodeMemoryPoolMock())

}
