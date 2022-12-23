package com.horizen.certificatesubmitter.strategy

import akka.actor.{ActorRef, ActorSystem}
import com.horizen.{MempoolSettings, SidechainHistory, SidechainMemoryPool, SidechainSettings, SidechainState, SidechainWallet}
import com.horizen.block.SidechainBlock
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter.{CertificateSignatureInfo, SignaturesStatus}
import com.horizen.certificatesubmitter.strategies.{CeasingSidechain, NonCeasingSidechain, SubmissionWindowStatus}
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
import scorex.util.ModifierId
import com.horizen.secret.{SchnorrKeyGenerator, SchnorrSecret}
import com.horizen.utils.BytesUtils
import com.horizen.websocket.client.{ChainTopQualityCertificateInfo, MainchainNodeChannel, MempoolTopQualityCertificateInfo, TopQualityCertificates}
import sparkz.core.NodeViewHolder.CurrentView

import java.nio.charset.StandardCharsets
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

class CeasingSidechainTest extends JUnitSuite
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
  val mainchainChannel = mock[MainchainNodeChannel]

  val withdrawalEpochLength= 100
  val params = MainNetParams(withdrawalEpochLength = withdrawalEpochLength, signersThreshold = 1)
  val ceasingSidechainStrategy = new CeasingSidechain(mainchainChannel, params)

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

    val schnorrSecret1 = SchnorrKeyGenerator.getInstance().generateSecret("seed1".getBytes(StandardCharsets.UTF_8))
    val schnorrSecret2 = SchnorrKeyGenerator.getInstance().generateSecret("seed2".getBytes(StandardCharsets.UTF_8))

    // Too few keys
    when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any[String])).thenAnswer(
      _ => Try {
        TopQualityCertificates(None, None)
      }
    )
    var status = SignaturesStatus(referencedEpochNumber, messageToSign, knownSigs)
    assertFalse("Quality check must fail.", ceasingSidechainStrategy.checkQuality(status))

    // No top quality certificates
    knownSigs.append(CertificateSignatureInfo(0, schnorrSecret1.sign(messageToSign)))
    when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any[String])).thenAnswer(
      _ => Try {TopQualityCertificates(None, None)}
    )

    status = SignaturesStatus(referencedEpochNumber, messageToSign, knownSigs)
    assertTrue("Quality check must be successful.", ceasingSidechainStrategy.checkQuality(status))

    // Top quality certificates in previous epoch
    when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any[String])).thenAnswer(
      _ => Try {
        // In-chain Cert in the MC
        TopQualityCertificates(
          None,
          Some(ChainTopQualityCertificateInfo("", referencedEpochNumber - 1, 2))
        )
      }
    )

    status = SignaturesStatus(referencedEpochNumber, messageToSign, knownSigs)
    assertTrue("Quality check must be successful.", ceasingSidechainStrategy.checkQuality(status))

    // Top quality certificate in mempool(lower quality)
    when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any[String])).thenAnswer(
      _ => Try {
        // In-chain Cert in the MC
        TopQualityCertificates(
          Some(MempoolTopQualityCertificateInfo("", referencedEpochNumber, 1, 0.0)),
          None
        )
      }
    )

    knownSigs.append(CertificateSignatureInfo(1, schnorrSecret2.sign(messageToSign)))

    status = SignaturesStatus(referencedEpochNumber, messageToSign, knownSigs)
    assertTrue("Quality check must be successful.", ceasingSidechainStrategy.checkQuality(status))

    // Top quality certificate in mempool(equal quality)
    when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any[String])).thenAnswer(
      _ => Try {
        // In-chain Cert in the MC
        TopQualityCertificates(
          Some(MempoolTopQualityCertificateInfo("", referencedEpochNumber, 2, 0.0)),
          None
        )
      }
    )

    status = SignaturesStatus(referencedEpochNumber, messageToSign, knownSigs)
    assertFalse("Quality check must fail.", ceasingSidechainStrategy.checkQuality(status))

    // Top quality certificate in chain(low quality)
    when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any[String])).thenAnswer(
      _ => Try {
        // In-chain Cert in the MC
        TopQualityCertificates(
          None,
          Some(ChainTopQualityCertificateInfo("", referencedEpochNumber, 1))
        )
      }
    )

    status = SignaturesStatus(referencedEpochNumber, messageToSign, knownSigs)
    assertTrue("Quality check must be successful.", ceasingSidechainStrategy.checkQuality(status))

    // Top quality certificate in chain(low quality)
    when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any[String])).thenAnswer(
      _ => Try {
        // In-chain Cert in the MC
        TopQualityCertificates(
          None,
          Some(ChainTopQualityCertificateInfo("", referencedEpochNumber, 2))
        )
      }
    )

    status = SignaturesStatus(referencedEpochNumber, messageToSign, knownSigs)
    assertFalse("Quality check must fail.", ceasingSidechainStrategy.checkQuality(status))
  }

  @Test
  def getStatusTest(): Unit = {
    val block: SidechainBlock = mock[SidechainBlock]
    val blockHash = new Array[Byte](32)
    scala.util.Random.nextBytes(blockHash)
    when(block.id).thenReturn(ModifierId @@ BytesUtils.toHexString(blockHash))

    // Withdral Epoch 0 block 1
    val epochInfoWE0 = WithdrawalEpochInfo(epoch = 0, lastEpochIndex = 1)
    when(history.blockInfoById(ArgumentMatchers.any[ModifierId])).thenAnswer(_ => {
      val blockInfo: SidechainBlockInfo = mock[SidechainBlockInfo]
      when(blockInfo.withdrawalEpochInfo).thenAnswer(_ => epochInfoWE0)
      blockInfo
    })

    when(state.lastCertificateReferencedEpoch()).thenAnswer(_ => None)
    var status: SubmissionWindowStatus = ceasingSidechainStrategy.getStatus(mockedNodeViewHolder, block.id)

    assertFalse("Epoch 0 block 1 must not be in withdrawal window", status.isInWindow)

    // Withdral Epoch 0 block 20
    val epochInfoWE0b20 = WithdrawalEpochInfo(epoch = 0, lastEpochIndex = 20)
    when(history.blockInfoById(ArgumentMatchers.any[ModifierId])).thenAnswer(_ => {
      val blockInfo: SidechainBlockInfo = mock[SidechainBlockInfo]
      when(blockInfo.withdrawalEpochInfo).thenAnswer(_ => epochInfoWE0b20)
      blockInfo
    })

    when(state.lastCertificateReferencedEpoch()).thenAnswer(_ => None)
    status = ceasingSidechainStrategy.getStatus(mockedNodeViewHolder, block.id)

    assertFalse("Epoch 0 block 20 must not be in withdrawal window", status.isInWindow)

    // Withdral Epoch 1 block 0
    val epochInfoWE1b0 = WithdrawalEpochInfo(epoch = 1, lastEpochIndex = 0)
    when(history.blockInfoById(ArgumentMatchers.any[ModifierId])).thenAnswer(_ => {
      val blockInfo: SidechainBlockInfo = mock[SidechainBlockInfo]
      when(blockInfo.withdrawalEpochInfo).thenAnswer(_ => epochInfoWE1b0)
      blockInfo
    })

    when(state.lastCertificateReferencedEpoch()).thenAnswer(_ => None)
    status = ceasingSidechainStrategy.getStatus(mockedNodeViewHolder, block.id)

    assertTrue("Epoch 1 block 0 must not be in withdrawal window", status.isInWindow)
    assertEquals("Withdrawal reference epoch number must be 0", status.referencedWithdrawalEpochNumber, 0)

    // Withdral Epoch 1 block 20
    val epochInfoWE1b20 = WithdrawalEpochInfo(epoch = 1, lastEpochIndex = 20)
    when(history.blockInfoById(ArgumentMatchers.any[ModifierId])).thenAnswer(_ => {
      val blockInfo: SidechainBlockInfo = mock[SidechainBlockInfo]
      when(blockInfo.withdrawalEpochInfo).thenAnswer(_ => epochInfoWE1b20)
      blockInfo
    })

    when(state.lastCertificateReferencedEpoch()).thenAnswer(_ => None)
    status = ceasingSidechainStrategy.getStatus(mockedNodeViewHolder, block.id)

    assertTrue("Epoch 1 block 20 must not be in withdrawal window", status.isInWindow)
    assertEquals("Withdrawal reference epoch number must be 0", status.referencedWithdrawalEpochNumber, 0)

    // Withdral Epoch 1 block 21
    val epochInfoWE1b21 = WithdrawalEpochInfo(epoch = 1, lastEpochIndex = 21)
    when(history.blockInfoById(ArgumentMatchers.any[ModifierId])).thenAnswer(_ => {
      val blockInfo: SidechainBlockInfo = mock[SidechainBlockInfo]
      when(blockInfo.withdrawalEpochInfo).thenAnswer(_ => epochInfoWE1b21)
      blockInfo
    })

    when(state.lastCertificateReferencedEpoch()).thenAnswer(_ => None)
    status = ceasingSidechainStrategy.getStatus(mockedNodeViewHolder, block.id)

    assertFalse("Epoch 1 block 21 must not be in withdrawal window", status.isInWindow)
    assertEquals("Withdrawal reference epoch number must be 0", status.referencedWithdrawalEpochNumber, 0)
  }
}