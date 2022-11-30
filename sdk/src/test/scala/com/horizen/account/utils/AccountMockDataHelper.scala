package com.horizen.account.utils

import com.horizen.SidechainTypes
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.history.AccountHistory
import com.horizen.params.NetworkParams
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
    history
  }

  def getMockedBlock(
      baseFee: BigInteger,
      gasUsed: Long,
      gasLimit: Long,
      blockId: scorex.util.ModifierId,
      parentBlockId: scorex.util.ModifierId
  ): AccountBlock = {
    val block: AccountBlock = mock[AccountBlock]
    Mockito.when(block.header).thenReturn(mock[AccountBlockHeader])
    Mockito.when(block.header.parentId).thenReturn(parentBlockId)
    Mockito.when(block.id).thenReturn(blockId)
    Mockito.when(block.header.baseFee).thenReturn(baseFee)
    Mockito.when(block.header.gasUsed).thenReturn(gasUsed)
    Mockito.when(block.header.gasLimit).thenReturn(gasLimit)
    Mockito.when(block.sidechainTransactions).thenReturn(Seq[SidechainTypes#SCAT]())

    block
  }

  def getMockedBlock2(txes: Seq[SidechainTypes#SCAT]): AccountBlock = {
    val block: AccountBlock = mock[AccountBlock]
    Mockito.when(block.transactions).thenReturn(txes)

    block
  }

}
