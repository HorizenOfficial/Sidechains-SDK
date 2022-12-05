package com.horizen.account.utils

import cats.instances.byte
import com.horizen.SidechainTypes
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.history.AccountHistory
import com.horizen.account.receipt.{EthereumReceipt, ReceiptFixture}
import com.horizen.account.state.{AccountState, AccountStateView}
import com.horizen.account.transaction.EthereumTransaction.EthereumTransactionType
import com.horizen.params.NetworkParams
import com.horizen.utils.BytesUtils
import org.mockito.ArgumentMatchers.{any, anyByte, anyInt}
import org.mockito.Mockito
import org.scalatestplus.mockito.MockitoSugar.mock
import scorex.util.bytesToId

import java.math.BigInteger
import java.util.Optional

case class AccountMockDataHelper(genesis: Boolean) {

  def getMockedAccountHistory(block: Optional[AccountBlock]): AccountHistory = {
    val history: AccountHistory = mock[AccountHistory]
    Mockito.when(history.params).thenReturn(mock[NetworkParams])
    if (genesis) {
      Mockito.when(history.params.sidechainGenesisBlockParentId).thenReturn(bytesToId(new Array[Byte](32)))
    }
    Mockito.when(history.getCurrentHeight).thenReturn(1)
    Mockito.when(history.getBlockById(block.get.id)).thenReturn(block)
    val x = Option(block.get.id)
    Mockito.when(history.blockIdByHeight(anyInt())).thenReturn(x)
    Mockito.when(history.getStorageBlockById(x.get)).thenReturn(Option(block.get()))
    history
  }

  def getMockedBlock(
      baseFee: BigInteger = FeeUtils.INITIAL_BASE_FEE,
      gasUsed: Long = 0L,
      gasLimit: Long = FeeUtils.GAS_LIMIT,
      blockId: scorex.util.ModifierId = null,
      parentBlockId: scorex.util.ModifierId = null,
      txs: Seq[SidechainTypes#SCAT] = null
  ): AccountBlock = {
    val block: AccountBlock = mock[AccountBlock]
    Mockito.when(block.header).thenReturn(mock[AccountBlockHeader])
    Mockito.when(block.header.parentId).thenReturn(parentBlockId)
    Mockito.when(block.id).thenReturn(blockId)
    Mockito.when(block.header.baseFee).thenReturn(baseFee)
    Mockito.when(block.header.gasUsed).thenReturn(gasUsed)
    Mockito.when(block.header.gasLimit).thenReturn(gasLimit)
    Mockito.when(block.sidechainTransactions).thenReturn(Seq[SidechainTypes#SCAT]())
    Mockito.when(block.transactions).thenReturn(txs)

    block
  }

  def getMockedState(receipt: EthereumReceipt, txHash: Array[Byte]): AccountState = {
    val state: AccountState = mock[AccountState]
    Mockito.when(state.getView).thenReturn(mock[AccountStateView])
    Mockito.when(state.getView.getTransactionReceipt(any())).thenReturn(None)
    Mockito.when(state.getView.getTransactionReceipt(txHash)).thenReturn(Option(receipt))
    state
  }

}
