package com.horizen.account.utils

import com.horizen.account.block.AccountBlock
import com.horizen.account.utils.AccountForwardTransfersHelper.getForwardTransfersForBlock
import com.horizen.block.MainchainBlockReferenceData
import com.horizen.utxo.box._
import com.horizen.fixtures._
import com.horizen.proposition._
import com.horizen.transaction.MC2SCAggregatedTransaction
import com.horizen.transaction.mainchain.{ForwardTransfer, SidechainCreation, SidechainRelatedMainchainOutput}
import com.horizen.utils.BytesUtils
import com.horizen.utxo.box.Box
import org.junit.Assert._
import org.junit._
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import scala.collection.JavaConverters._


class AccountForwardTransfersHelperTest
  extends JUnitSuite
    with SidechainRelatedMainchainOutputFixture
    with MockitoSugar
{
  val scId: Array[Byte] = BytesUtils.fromHexString("0741cf4bc4c2098a193865a6eba217a44a434bc6754f1d230ceeabc57e6784e5")

  @Test
  def testCalculateForwardTransferList(): Unit = {

    // Test 1: RefData without MC2SCAggTx
    val emptyRefData: MainchainBlockReferenceData = MainchainBlockReferenceData(null, sidechainRelatedAggregatedTransaction = None, None, None, Seq(), None)
    val mockedEmptyBlock = mock[AccountBlock]
    Mockito.when(mockedEmptyBlock.mainchainBlockReferencesData).thenReturn(Seq(emptyRefData))
    val emptyFtSeq = getForwardTransfersForBlock(mockedEmptyBlock)
    assertTrue(emptyFtSeq.isEmpty)


    // Test 2: RefData with MC2SCAggTx with FTs and a ScCr
    val ft1: ForwardTransfer = getForwardTransfer(getPrivateKey25519.publicImage(), scId)
    val ft2: ForwardTransfer = getForwardTransfer(getPrivateKey25519.publicImage(), scId)
    val sc1 : SidechainCreation = getDummyScCreation(new Array[Byte](32))
    val seqOutput: Seq[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]] = Seq(ft1, sc1, ft2)

    val mc2scTransactionsOutputs: Seq[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]] = seqOutput
    val aggTx = new MC2SCAggregatedTransaction(mc2scTransactionsOutputs.asJava, MC2SCAggregatedTransaction.MC2SC_AGGREGATED_TRANSACTION_VERSION)
    val refDataWithFTs: MainchainBlockReferenceData = MainchainBlockReferenceData(null, Some(aggTx), None, None, Seq(), None)
    val mockedBlock = mock[AccountBlock]
    Mockito.when(mockedBlock.mainchainBlockReferencesData).thenReturn(Seq(refDataWithFTs))

    val fts = getForwardTransfersForBlock(mockedBlock)
    val seqFt: Seq[ForwardTransfer] = Seq(ft1, ft2)
    assertEquals(fts, seqFt)
    println(fts)
  }
}
