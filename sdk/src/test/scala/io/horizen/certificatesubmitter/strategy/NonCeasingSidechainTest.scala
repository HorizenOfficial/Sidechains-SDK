package io.horizen.certificatesubmitter.strategy

import akka.actor.{ActorRef, ActorSystem}
import io.horizen.certificatesubmitter.AbstractCertificateSubmitter.{CertificateSignatureInfo, SignaturesStatus}
import io.horizen.certificatesubmitter.strategies.{NonCeasingSidechain, SubmissionWindowStatus}
import io.horizen.chain.SidechainBlockInfo
import io.horizen.fixtures.SidechainBlockFixture.sidechainTransactionsCompanion
import io.horizen.fixtures.{FieldElementFixture, MockedSidechainNodeViewHolderFixture, SidechainBlockFixture}
import io.horizen.params.MainNetParams
import io.horizen.proposition.SchnorrProposition
import io.horizen.secret.SchnorrKeyGenerator
import io.horizen.utils.{BytesUtils, WithdrawalEpochInfo}
import io.horizen.utxo.block.SidechainBlock
import io.horizen.utxo.history.SidechainHistory
import io.horizen.utxo.mempool.SidechainMemoryPool
import io.horizen.utxo.state.SidechainState
import io.horizen.utxo.wallet.SidechainWallet
import io.horizen.websocket.client.{ChainTopQualityCertificateInfo, MainchainNodeChannel, MempoolTopQualityCertificateInfo, TopQualityCertificates}
import io.horizen.{MempoolSettings, SidechainSettings}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.{Before, Test}
import org.mockito.Mockito.when
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.util.ModifierId

import java.nio.charset.StandardCharsets
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

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
  private val mainchainChannel = mock[MainchainNodeChannel]
  private val nonCeasingSidechainStrategy = new NonCeasingSidechain(mainchainChannel,params)

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
    val signersPublicKeys: Seq[SchnorrProposition] = Seq(schnorrSecret1.publicImage(), schnorrSecret2.publicImage())

    knownSigs.append(CertificateSignatureInfo(0, schnorrSecret1.sign(messageToSign)))
    knownSigs.append(CertificateSignatureInfo(1, schnorrSecret2.sign(messageToSign)))

    val signaturesStatusSuccess = SignaturesStatus(referencedEpochNumber, messageToSign, knownSigs, signersPublicKeys)

    // test - no certificate present
    when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any[String])).thenAnswer(
      _ => Try {
        TopQualityCertificates(None, None)
      }
    )
    assertTrue("Quality check must be successful.", nonCeasingSidechainStrategy.checkQuality(signaturesStatusSuccess))

    knownSigs.clear()
    knownSigs.append(CertificateSignatureInfo(0, schnorrSecret1.sign(messageToSign)))
    val signaturesStatusFail = SignaturesStatus(referencedEpochNumber, messageToSign, knownSigs, signersPublicKeys)
    assertFalse("Quality check must fail.", nonCeasingSidechainStrategy.checkQuality(signaturesStatusFail))

    knownSigs.append(CertificateSignatureInfo(1, schnorrSecret2.sign(messageToSign)))

    // test - previous epoch certificate present
    Mockito.when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any())).thenAnswer(_ => {
      Try {
        TopQualityCertificates(
          None,
          Some(ChainTopQualityCertificateInfo("", referencedEpochNumber - 1, 2))
        )
      }
    })
    val status = SignaturesStatus(referencedEpochNumber, messageToSign, knownSigs, signersPublicKeys)
    assertTrue("Quality check must be successful.", nonCeasingSidechainStrategy.checkQuality(status))

    //test - same quality certificate present
    Mockito.when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any())).thenAnswer(_ => {
      Try {
        TopQualityCertificates(
          None,
          Some(ChainTopQualityCertificateInfo("", referencedEpochNumber, 2))
        )
      }
    })
    assertFalse("Quality check must fail.", nonCeasingSidechainStrategy.checkQuality(status))

    //test - lower quality certificate present
    Mockito.when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any())).thenAnswer(_ => {
      Try {
        TopQualityCertificates(
          None,
          Some(ChainTopQualityCertificateInfo("", referencedEpochNumber, 1))
        )
      }
    })
    assertFalse("Quality check must fail.", nonCeasingSidechainStrategy.checkQuality(status))

    //test - higher quality certificate present
    Mockito.when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any())).thenAnswer(_ => {
      Try {
        TopQualityCertificates(
          None,
          Some(ChainTopQualityCertificateInfo("", referencedEpochNumber, 3))
        )
      }
    })
    assertFalse("Quality check must fail.", nonCeasingSidechainStrategy.checkQuality(status))

    //test - next epoch certificate present
    Mockito.when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any())).thenAnswer(_ => {
      Try {
        TopQualityCertificates(
          None,
          Some(ChainTopQualityCertificateInfo("", referencedEpochNumber + 1, 1))
        )
      }
    })
    assertFalse("Quality check must fail.", nonCeasingSidechainStrategy.checkQuality(status))

    // test - previous epoch certificate present in mempool
    Mockito.when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any())).thenAnswer(_ => {
      Try {
        TopQualityCertificates(
          Some(MempoolTopQualityCertificateInfo("", referencedEpochNumber - 1, 2, 0.0)),
          None
        )
      }
    })
    assertTrue("Quality check must be successful.", nonCeasingSidechainStrategy.checkQuality(status))

    //test - same quality certificate present in mempool
    Mockito.when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any())).thenAnswer(_ => {
      Try {
        TopQualityCertificates(
          Some(MempoolTopQualityCertificateInfo("", referencedEpochNumber, 2, 0.0)),
          None
        )
      }
    })
    assertFalse("Quality check must fail.", nonCeasingSidechainStrategy.checkQuality(status))

    //test - lower quality certificate present
    Mockito.when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any())).thenAnswer(_ => {
      Try {
        TopQualityCertificates(
          Some(MempoolTopQualityCertificateInfo("", referencedEpochNumber, 1, 0.0)),
          None
        )
      }
    })
    assertFalse("Quality check must fail.", nonCeasingSidechainStrategy.checkQuality(status))

    //test - higher quality certificate present
    Mockito.when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any())).thenAnswer(_ => {
      Try {
        TopQualityCertificates(
          Some(MempoolTopQualityCertificateInfo("", referencedEpochNumber, 3, 0.0)),
          None
        )
      }
    })
    assertFalse("Quality check must fail.", nonCeasingSidechainStrategy.checkQuality(status))

    //test - next epoch certificate present
    Mockito.when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any())).thenAnswer(_ => {
      Try {
        TopQualityCertificates(
          Some(MempoolTopQualityCertificateInfo("", referencedEpochNumber + 1, 1, 0.0)),
          None
        )
      }
    })
    assertFalse("Quality check must fail.", nonCeasingSidechainStrategy.checkQuality(status))

    //test - next epoch certificate present in mempool, but not in chain
    Mockito.when(mainchainChannel.getTopQualityCertificates(ArgumentMatchers.any())).thenAnswer(_ => {
      Try {
        TopQualityCertificates(
          Some(MempoolTopQualityCertificateInfo("", referencedEpochNumber + 1, 1, 0.0)),
          Some(ChainTopQualityCertificateInfo("", referencedEpochNumber, 1))
        )
      }
    })
    assertFalse("Quality check must fail.", nonCeasingSidechainStrategy.checkQuality(status))
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