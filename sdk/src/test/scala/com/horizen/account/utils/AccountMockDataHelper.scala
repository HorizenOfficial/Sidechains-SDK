package com.horizen.account.utils

import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.history.AccountHistory
import org.mockito.Mockito
import org.scalatestplus.mockito.MockitoSugar.mock
import org.web3j.utils.Numeric
import scorex.util.bytesToId

import java.math.BigInteger
import java.util.Optional

case class AccountMockDataHelper(genesis: Boolean) {

  def getMockedAccountHistory(block: Optional[AccountBlock]): AccountHistory = {
    val history: AccountHistory = mock[AccountHistory]
    if (!genesis) Mockito.when(history.getBlockById(block.get.id)).thenReturn(block)
    Mockito.when(history.isGenesisBlock(block.get.id)).thenReturn(genesis)
    history
  }

  def getMockedBlock(baseFee: BigInteger): AccountBlock = {
    val block: AccountBlock = mock[AccountBlock]
    Mockito.when(block.header).thenReturn(mock[AccountBlockHeader])
    if (genesis) Mockito.when(block.id).thenReturn(bytesToId(Numeric.hexStringToByteArray("123")))
    else Mockito.when(block.header.parentId).thenReturn(bytesToId(Numeric.hexStringToByteArray("123")))
    Mockito.when(block.header.baseFee).thenReturn(baseFee)
    block
  }

}
