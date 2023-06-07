package io.horizen.account.sc2sc

import io.horizen.account.fixtures.AccountCrossChainMessageFixture
import io.horizen.account.sc2sc.CrossChainRedeemMessageProcessorImpl.{contractAddress, nextScCommitmentTreeRoot, receiverSidechain, scCommitmentTreeRoot}
import io.horizen.account.state._
import io.horizen.cryptolibprovider.Sc2scCircuit
import io.horizen.evm.Address
import io.horizen.params.NetworkParams
import io.horizen.sc2sc.CrossChainMessageHash
import io.horizen.utils.BytesUtils
import org.junit.Assert.{assertEquals, assertTrue, fail}
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.Assertions.intercept
import org.scalatestplus.mockito.MockitoSugar.mock
import sparkz.crypto.hash.Keccak256

class AbstractCrossChainRedeemMessageProcessorTest extends MessageProcessorFixture with AccountCrossChainMessageFixture {

  private val mockStateView: AccountStateView = mock[AccountStateView]
  private val networkParamsMock = mock[NetworkParams]
  private val sc2scCircuitMock = mock[Sc2scCircuit]
  private val receiverSidechain = CrossChainRedeemMessageProcessorImpl.receiverSidechain
  private val scTxCommProvider = mock[ScTxCommitmentTreeRootHashMessageProvider]

  @Test
  def whenReceivingScIdIsDifferentThenTheScIdInSettings_throwsAnIllegalArgumentException(): Unit = {
    // Arrange
    val msg = mock[Message]

    val badScId = BytesUtils.fromHexString("7a03386bd56e577d5b99a40e61278d35ef455bd67f6ccc2825d9c1e834ddb623")
    when(networkParamsMock.sidechainId).thenReturn(badScId)

    val ccRedeemMsgProcessor: AbstractCrossChainRedeemMessageProcessor = new CrossChainRedeemMessageProcessorImpl(networkParamsMock.sidechainId, networkParamsMock.sc2ScVerificationKeyFilePath, sc2scCircuitMock, scTxCommProvider)

    // Act
    val exception = intercept[ExecutionRevertedException] {
      withGas(ccRedeemMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    }

    // Assert
    val expectedMsg = s"This scId `${BytesUtils.toHexString(badScId)}` and receiving scId `${BytesUtils.toHexString(receiverSidechain)}` do not match"
    assertEquals(expectedMsg, exception.getCause.getMessage)
  }

  @Test
  def whenTryToRedeemTheSameMessageTwice_throwsAnIllegalArgumentException(): Unit = {
    // Arrange
    val msg = mock[Message]

    when(networkParamsMock.sidechainId).thenReturn(receiverSidechain)
    when(mockStateView.doesCrossChainMessageHashFromRedeemMessageExist(any())).thenReturn(true)

    val ccRedeemMsgProcessor: AbstractCrossChainRedeemMessageProcessor = new CrossChainRedeemMessageProcessorImpl(networkParamsMock.sidechainId, networkParamsMock.sc2ScVerificationKeyFilePath, sc2scCircuitMock, scTxCommProvider)

    // Act
    val exception = intercept[ExecutionRevertedException] {
      withGas(ccRedeemMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    }

    // Assert
    val expectedMsg = s" has already been redeemed"
    assertTrue(exception.getCause.getMessage.contains(expectedMsg))
  }

  @Test
  def whenScTxCommitmentTreeHashDoesNotExist_throwsAnIllegalArgumentException(): Unit = {
    // Arrange
    val msg = mock[Message]

    when(networkParamsMock.sidechainId).thenReturn(receiverSidechain)
    when(mockStateView.getAccountStorage(ArgumentMatchers.eq(contractAddress), any())).thenReturn(Array.emptyByteArray)
    when(scTxCommProvider.doesScTxCommitmentTreeRootHashExist(scCommitmentTreeRoot, mockStateView)).thenReturn(false)

    val ccRedeemMsgProcessor: AbstractCrossChainRedeemMessageProcessor = new CrossChainRedeemMessageProcessorImpl(networkParamsMock.sidechainId, networkParamsMock.sc2ScVerificationKeyFilePath, sc2scCircuitMock, scTxCommProvider)

    // Act
    val exception = intercept[ExecutionRevertedException] {
      withGas(ccRedeemMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    }

    // Assert
    val expectedMsg = s"Sidechain commitment tree root `${BytesUtils.toHexString(scCommitmentTreeRoot)}` does not exist"
    assertEquals(expectedMsg, exception.getCause.getMessage)
  }

  @Test
  def whenScNextTxCommitmentTreeHashDoesNotExist_throwsAnIllegalArgumentException(): Unit = {
    // Arrange
    val msg = mock[Message]

    when(networkParamsMock.sidechainId).thenReturn(receiverSidechain)
    when(mockStateView.getAccountStorage(ArgumentMatchers.eq(contractAddress), any())).thenReturn(Array.emptyByteArray)
    when(scTxCommProvider.doesScTxCommitmentTreeRootHashExist(scCommitmentTreeRoot, mockStateView)).thenReturn(true)
    when(scTxCommProvider.doesScTxCommitmentTreeRootHashExist(nextScCommitmentTreeRoot, mockStateView)).thenReturn(false)

    val ccRedeemMsgProcessor: AbstractCrossChainRedeemMessageProcessor = new CrossChainRedeemMessageProcessorImpl(networkParamsMock.sidechainId, networkParamsMock.sc2ScVerificationKeyFilePath, sc2scCircuitMock, scTxCommProvider)

    // Act
    val exception = intercept[ExecutionRevertedException] {
      withGas(ccRedeemMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    }

    // Assert
    val expectedMsg = s"Sidechain next commitment tree root `${BytesUtils.toHexString(nextScCommitmentTreeRoot)}` does not exist"
    assertEquals(expectedMsg, exception.getCause.getMessage)
  }

  @Test
  def whenProofCannotBeVerified_throwsAnIllegalArgumentException(): Unit = {
    // Arrange
    val msg = mock[Message]

    when(networkParamsMock.sidechainId).thenReturn(receiverSidechain)
    when(mockStateView.getAccountStorage(ArgumentMatchers.eq(contractAddress), any())).thenReturn(Array.emptyByteArray)
    when(scTxCommProvider.doesScTxCommitmentTreeRootHashExist(scCommitmentTreeRoot, mockStateView)).thenReturn(true)
    when(scTxCommProvider.doesScTxCommitmentTreeRootHashExist(nextScCommitmentTreeRoot, mockStateView)).thenReturn(true)
    when(sc2scCircuitMock.verifyRedeemProof(any(), any(), any(), any(), any())).thenReturn(false)
    when(networkParamsMock.sc2ScVerificationKeyFilePath).thenReturn(Some("sc2ScVerificationKeyFilePath"))

    val ccRedeemMsgProcessor: AbstractCrossChainRedeemMessageProcessor = new CrossChainRedeemMessageProcessorImpl(networkParamsMock.sidechainId, networkParamsMock.sc2ScVerificationKeyFilePath, sc2scCircuitMock, scTxCommProvider)

    // Act
    val exception = intercept[ExecutionRevertedException] {
      withGas(ccRedeemMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    }

    // Assert
    val expectedMsg = s"Cannot verify this cross-chain message"
    assertTrue(exception.getCause.getMessage.contains(expectedMsg))
  }

  @Test
  def whenAllValidationsPass_throwsNoException(): Unit = {
    // Arrange
    val msg = mock[Message]

    when(networkParamsMock.sidechainId).thenReturn(receiverSidechain)
    when(mockStateView.getAccountStorage(ArgumentMatchers.eq(contractAddress), any())).thenReturn(Array.emptyByteArray)
    when(scTxCommProvider.doesScTxCommitmentTreeRootHashExist(scCommitmentTreeRoot, mockStateView)).thenReturn(true)
    when(scTxCommProvider.doesScTxCommitmentTreeRootHashExist(nextScCommitmentTreeRoot, mockStateView)).thenReturn(true)
    when(sc2scCircuitMock.verifyRedeemProof(any(), any(), any(), any(), any())).thenReturn(true)
    when(networkParamsMock.sc2ScVerificationKeyFilePath).thenReturn(Some("sc2ScVerificationKeyFilePath"))

    val ccRedeemMsgProcessor: AbstractCrossChainRedeemMessageProcessor = new CrossChainRedeemMessageProcessorImpl(networkParamsMock.sidechainId, networkParamsMock.sc2ScVerificationKeyFilePath, sc2scCircuitMock, scTxCommProvider)

    // Act & Assert
    try withGas(ccRedeemMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    catch {
      case e: Exception =>
        fail(s"Test failed unexpectedly ${e.getMessage}")
    }
  }
}

class CrossChainRedeemMessageProcessorImpl(scId: Array[Byte], path: Option[String], sc2scCircuit: Sc2scCircuit, scTxMsgProc: ScTxCommitmentTreeRootHashMessageProvider)
  extends AbstractCrossChainRedeemMessageProcessor(scId, path, sc2scCircuit, scTxMsgProc) {

  override val contractAddress: Address = CrossChainRedeemMessageProcessorImpl.contractAddress
  override val contractCode: Array[Byte] = CrossChainRedeemMessageProcessorImpl.contractCode

  override protected def getAccountCrossChainRedeemMessageFromMessage(msg: Message): AccountCrossChainRedeemMessage = {
    val messageType = 1
    val sender = "d504dbfde192182c68d2".getBytes
    val receiver = "0303908afe9d1078bdf1".getBytes
    val payload = "1234".getBytes

    val certificateDataHash = BytesUtils.fromHexString("8b4a3cf70f33a2b9692d1bd5c612e2903297b35289e59c9be7afa0984befd230")
    val nextCertificateDataHash = BytesUtils.fromHexString("1701e3d5c949797c469644a8c7ff495ee28259c5548d7879fcc5518fe1e2163c")
    val scCommitmentTreeRoot = CrossChainRedeemMessageProcessorImpl.scCommitmentTreeRoot
    val nextScCommitmentTreeRoot = CrossChainRedeemMessageProcessorImpl.nextScCommitmentTreeRoot
    val proof = "proof".getBytes
    AccountCrossChainRedeemMessage(
      messageType, sender, receiverSidechain, receiver, payload, certificateDataHash, nextCertificateDataHash, scCommitmentTreeRoot, nextScCommitmentTreeRoot, proof
    )
  }

  /**
   * Apply message to the given view. Possible results:
   * <ul>
   * <li>applied as expected: return byte[]</li>
   * <li>message valid and (partially) executed, but operation "failed": throw ExecutionFailedException</li>
   * <li>message invalid and must not exist in a block: throw any other Exception</li>
   * </ul>
   *
   * @param msg          message to apply to the state
   * @param view         state view
   * @param gas          available gas for the execution
   * @param blockContext contextual information accessible during execution
   * @return return data on successful execution
   * @throws ExecutionRevertedException revert-and-keep-gas-left, also mark the message as "failed"
   * @throws ExecutionFailedException   revert-and-consume-all-gas, also mark the message as "failed"
   * @throws RuntimeException           any other exceptions are consideres as "invalid message"
   */
  @throws(classOf[ExecutionFailedException])
  override def process(msg: Message, view: BaseAccountStateView, gas: GasPool, blockContext: BlockContext): Array[Byte] = {
    processRedeemMessage(getAccountCrossChainRedeemMessageFromMessage(msg), view)
  }
}

object CrossChainRedeemMessageProcessorImpl extends CrossChainMessageProcessorConstants {
  val contractAddress: Address = new Address("0x35fdd51e73221f467b40946c97791a3e19799bea")
  val contractCode: Array[Byte] = Keccak256.hash("CrossChainRedeemMessageProcessorImplCode")

  val receiverSidechain: Array[Byte] = BytesUtils.fromHexString("237a03386bd56e577d5b99a40e61278d35ef455bd67f6ccc2825d9c1e834ddb6")
  val scCommitmentTreeRoot: Array[Byte] = BytesUtils.fromHexString("05a1b84478667437c79b4dcd8948c8fd6ff624b7af22f92897dce10ccfb2147d")
  val nextScCommitmentTreeRoot: Array[Byte] = BytesUtils.fromHexString("3ebf08d8d1176d945209599be3b61c2e2e96d6e118baf146b77cf53e2f9a39d0")
}