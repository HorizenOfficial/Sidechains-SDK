package com.horizen.certificatesubmitter.strategy

import akka.actor.{ActorRef, ActorSystem}
import com.horizen.{MempoolSettings, SidechainHistory, SidechainMemoryPool, SidechainSettings, SidechainState, SidechainWallet}
import com.horizen.block.SidechainBlock
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter.{CertificateSignatureInfo, SignaturesStatus}
import com.horizen.certificatesubmitter.strategies.{NonCeasingSidechain, SubmissionWindowStatus}
import com.horizen.chain.SidechainBlockInfo
import com.horizen.fixtures.SidechainBlockFixture.sidechainTransactionsCompanion
import com.horizen.fixtures.{FieldElementFixture, MockedSidechainNodeViewHolder, MockedSidechainNodeViewHolderFixture, SidechainBlockFixture}
import com.horizen.params.MainNetParams
import com.horizen.utils.WithdrawalEpochInfo
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.{Before, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.mockito.Mockito.when
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar.mock
import sparkz.util.ModifierId
import com.horizen.secret.{SchnorrKeyGenerator, SchnorrSecret}
import com.horizen.utils.BytesUtils
import sparkz.core.NodeViewHolder.CurrentView

import scala.collection.mutable.ArrayBuffer

class NonCeasingSidechainTest extends JUnitSuite
  with MockedSidechainNodeViewHolderFixture
  with SidechainBlockFixture
{

  var history: SidechainHistory = _
  var state: SidechainState = _
  var wallet: SidechainWallet = _
  var mempool: SidechainMemoryPool = _
  var mockedNodeViewHolderRef: ActorRef = _
  implicit val actorSystem: ActorSystem = ActorSystem("sc_nvh_mocked")
  val genesisBlock: SidechainBlock = SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion)
  var settings: SidechainSettings = _
  var mockedNodeViewHolder: CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool] = _

  private val params = MainNetParams(signersThreshold = 2)
  private val nonCeasingSidechainStrategy = new NonCeasingSidechain(params)

  @Before
  def setUp(): Unit = {
    history = mock[SidechainHistory]
    state = mock[SidechainState]
    wallet = mock[SidechainWallet]
    mempool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(300))
    settings = mock[SidechainSettings]
    mockedNodeViewHolder = CurrentView(history, state, wallet, mempool)
  }

  private def getMockedMempoolSettings(maxSize: Int): MempoolSettings = {
    val mockedSettings: MempoolSettings = mock[MempoolSettings]
    Mockito.when(mockedSettings.maxSize).thenReturn(maxSize)
    Mockito.when(mockedSettings.minFeeRate).thenReturn(0)
    mockedSettings
  }

  @Test
  def checkQualityTest(): Unit = {
    val referencedEpochNumber = 10
    val messageToSign = FieldElementFixture.generateFieldElement()
    val knownSigs = ArrayBuffer[CertificateSignatureInfo]()

    val schnorrSecret1 = SchnorrKeyGenerator.getInstance().generateSecret("seed1".getBytes())
    val schnorrSecret2 = SchnorrKeyGenerator.getInstance().generateSecret("seed2".getBytes())

    knownSigs.append(CertificateSignatureInfo(0, schnorrSecret1.sign(messageToSign)))
    knownSigs.append(CertificateSignatureInfo(1, schnorrSecret2.sign(messageToSign)))

    val signaturesStatusSuccess = SignaturesStatus(referencedEpochNumber, messageToSign, knownSigs)

    assertTrue("Quality check must be successful.", nonCeasingSidechainStrategy.checkQuality(signaturesStatusSuccess))

    knownSigs.clear()
    knownSigs.append(CertificateSignatureInfo(0, schnorrSecret1.sign(messageToSign)))
    val signaturesStatusFail = SignaturesStatus(referencedEpochNumber, messageToSign, knownSigs)
    assertFalse("Quality check must fail.", nonCeasingSidechainStrategy.checkQuality(signaturesStatusFail))
  }

  @Test
  def getStatusTest(): Unit = {
    val block: SidechainBlock = mock[SidechainBlock]
    val blockHash = new Array[Byte](32)
    scala.util.Random.nextBytes(blockHash)
    Mockito.when(block.id).thenReturn(ModifierId @@ BytesUtils.toHexString(blockHash))

    // Withdral Epoch 0 block 1
    val epochInfoWE0 = WithdrawalEpochInfo(epoch = 0, lastEpochIndex = 1)
    when(history.blockInfoById(ArgumentMatchers.any[ModifierId])).thenAnswer(_ => {
      val blockInfo: SidechainBlockInfo = mock[SidechainBlockInfo]
      when(blockInfo.withdrawalEpochInfo).thenAnswer(_ => epochInfoWE0)
      blockInfo
    })

    when(state.lastCertificateReferencedEpoch()).thenAnswer(_ => None)
    var status: SubmissionWindowStatus = nonCeasingSidechainStrategy.getStatus(mockedNodeViewHolder.history, mockedNodeViewHolder.state, block.id)

    assertFalse("Epoch 0 block 1 must not be in withdrawal window", status.isInWindow)
    assertEquals("Withdrawal reference epoch number must be 0", status.referencedWithdrawalEpochNumber, 0)

    // Withdral Epoch 1 block 0
    val epochInfoWE1b0 = WithdrawalEpochInfo(epoch = 1, lastEpochIndex = 0)
    when(history.blockInfoById(ArgumentMatchers.any[ModifierId])).thenAnswer(_ => {
      val blockInfo: SidechainBlockInfo = mock[SidechainBlockInfo]
      when(blockInfo.withdrawalEpochInfo).thenAnswer(_ => epochInfoWE1b0)
      blockInfo
    })

    when(state.lastCertificateReferencedEpoch()).thenAnswer(_ => None)
    status = nonCeasingSidechainStrategy.getStatus(mockedNodeViewHolder.history, mockedNodeViewHolder.state, block.id)

    assertFalse("Epoch 1 block 0 must not be in withdrawal window", status.isInWindow)
    assertEquals("Withdrawal reference epoch number must be 0", status.referencedWithdrawalEpochNumber, 0)

    // Withdral Epoch 1 block 1
    val epochInfoWE1b1 = WithdrawalEpochInfo(epoch = 1, lastEpochIndex = 1)
    when(history.blockInfoById(ArgumentMatchers.any[ModifierId])).thenAnswer(_ => {
      val blockInfo: SidechainBlockInfo = mock[SidechainBlockInfo]
      when(blockInfo.withdrawalEpochInfo).thenAnswer(_ => epochInfoWE1b1)
      blockInfo
    })

    when(state.lastCertificateReferencedEpoch()).thenAnswer(_ => None)

    status = nonCeasingSidechainStrategy.getStatus(mockedNodeViewHolder.history, mockedNodeViewHolder.state, block.id)

    assertTrue("Epoch 1 block 1 must be in withdrawal window", status.isInWindow)
    assertEquals("Withdrawal reference epoch number must be 0", status.referencedWithdrawalEpochNumber, 0)

    // Withdral Epoch 1 block 1 with previous certificate
    when(state.lastCertificateReferencedEpoch()).thenAnswer(_ => Some(0))

    status = nonCeasingSidechainStrategy.getStatus(mockedNodeViewHolder.history, mockedNodeViewHolder.state, block.id)

    assertFalse("Epoch 1 block 1 with last certepoch = 0 must not be in withdrawal window", status.isInWindow)
    assertEquals("Withdrawal reference epoch number must be 1", status.referencedWithdrawalEpochNumber, 1)

    // Withdral Epoch 2 block 0 without previous certificate
    val epochInfoWE2b0 = WithdrawalEpochInfo(epoch = 2, lastEpochIndex = 0)
    when(history.blockInfoById(ArgumentMatchers.any[ModifierId])).thenAnswer(_ => {
      val blockInfo: SidechainBlockInfo = mock[SidechainBlockInfo]
      when(blockInfo.withdrawalEpochInfo).thenAnswer(_ => epochInfoWE2b0)
      blockInfo
    })
    when(state.lastCertificateReferencedEpoch()).thenAnswer(_ => None)

    status = nonCeasingSidechainStrategy.getStatus(mockedNodeViewHolder.history, mockedNodeViewHolder.state, block.id)

    assertTrue("Epoch 2 block 0 without last certificate must be in withdrawal window", status.isInWindow)
    assertEquals("Withdrawal reference epoch number must be 0", status.referencedWithdrawalEpochNumber, 0)

    // Withdral Epoch 2 block 0 with previous certificate
    when(state.lastCertificateReferencedEpoch()).thenAnswer(_ => Some(0))

    status = nonCeasingSidechainStrategy.getStatus(mockedNodeViewHolder.history, mockedNodeViewHolder.state, block.id)

    assertFalse("Epoch 2 block 0 with last cert epoch = 0 must not be in withdrawal window", status.isInWindow)
    assertEquals("Withdrawal reference epoch number must be 1", status.referencedWithdrawalEpochNumber, 1)
  }
}