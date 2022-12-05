package com.horizen.account.utils

import com.horizen.SidechainTypes
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.history.AccountHistory
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.receipt.EthereumReceipt
import com.horizen.account.state.{AccountState, AccountStateView}
import com.horizen.block.MainchainBlockReference
import com.horizen.chain.{MainchainHeaderBaseInfo, SidechainBlockInfo}
import com.horizen.fixtures.SidechainBlockFixture.generateMainchainBlockReference
import com.horizen.fixtures.{FieldElementFixture, VrfGenerator}
import com.horizen.params.NetworkParams
import com.horizen.utils.{BytesUtils, WithdrawalEpochInfo}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.scalatestplus.mockito.MockitoSugar.mock
import scorex.util.bytesToId
import sparkz.core.consensus.ModifierSemanticValidity

import java.math.BigInteger
import java.util.Optional

case class AccountMockDataHelper(genesis: Boolean) {

  def getMockedAccountHistory(
      block: Optional[AccountBlock],
      parentBlock: Optional[AccountBlock] = null,
      genesisBlockId: Option[String] = Option.empty
  ): AccountHistory = {
    val history: AccountHistory = mock[AccountHistory]
    val blockId = block.get.id
    val parentId = parentBlock.get().id
    val height = if (genesis) 2 else 1
    val blockInfo = new SidechainBlockInfo(
      height,
      0,
      parentId,
      86400L * 2,
      ModifierSemanticValidity.Unknown,
      MainchainHeaderBaseInfo
        .getMainchainHeaderBaseInfoSeqFromBlock(block.get(), FieldElementFixture.generateFieldElement()),
      SidechainBlockInfo.mainchainReferenceDataHeaderHashesFromBlock(block.get()),
      WithdrawalEpochInfo(0, 0),
      Option(VrfGenerator.generateVrfOutput(0)),
      parentId
    )

    Mockito.when(history.params).thenReturn(mock[NetworkParams])

    Mockito.when(history.blockIdByHeight(any())).thenReturn(Option.empty[String])
    Mockito.when(history.blockIdByHeight(2)).thenReturn(Option(blockId))

    if (genesis) {
      Mockito.when(history.params.sidechainGenesisBlockParentId).thenReturn(bytesToId(new Array[Byte](32)))
      Mockito.when(history.blockIdByHeight(1)).thenReturn(genesisBlockId)
    }
    Mockito.when(history.getCurrentHeight).thenReturn(height)

    Mockito.when(history.getBlockById(any())).thenReturn(Optional.empty[AccountBlock])
    Mockito.when(history.getBlockById(blockId)).thenReturn(block)
    Mockito.when(history.getBlockById(parentId)).thenReturn(parentBlock)

    Mockito.when(history.getStorageBlockById(any())).thenReturn(Option.empty[AccountBlock])
    Mockito.when(history.getStorageBlockById(blockId)).thenReturn(Option(block.get()))
    Mockito.when(history.getStorageBlockById(parentId)).thenReturn(Option(parentBlock.get()))
    Mockito.when(history.blockInfoById(blockId)).thenReturn(blockInfo)
    history
  }

  def getMockedBlock(
      baseFee: BigInteger = FeeUtils.INITIAL_BASE_FEE,
      gasUsed: Long = 0L,
      gasLimit: Long = FeeUtils.GAS_LIMIT,
      blockId: scorex.util.ModifierId = null,
      parentBlockId: scorex.util.ModifierId = null,
      txs: Seq[SidechainTypes#SCAT] = Seq.empty[SidechainTypes#SCAT]
  ): AccountBlock = {
    val block: AccountBlock = mock[AccountBlock]
    val mcBlockRef: MainchainBlockReference = generateMainchainBlockReference()
    Mockito.when(block.header).thenReturn(mock[AccountBlockHeader])
    Mockito.when(block.header.parentId).thenReturn(parentBlockId)
    Mockito.when(block.id).thenReturn(blockId)
    Mockito.when(block.header.baseFee).thenReturn(baseFee)
    Mockito.when(block.header.gasUsed).thenReturn(gasUsed)
    Mockito.when(block.header.gasLimit).thenReturn(gasLimit)
    Mockito
      .when(block.header.forgerAddress)
      .thenReturn(new AddressProposition(BytesUtils.fromHexString("1234567891011121314112345678910111213141")))
    Mockito.when(block.sidechainTransactions).thenReturn(Seq[SidechainTypes#SCAT]())
    Mockito.when(block.transactions).thenReturn(txs)
    Mockito.when(block.mainchainHeaders).thenReturn(Seq(mcBlockRef.header))
    Mockito.when(block.mainchainBlockReferencesData).thenReturn(Seq(mcBlockRef.data))

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
