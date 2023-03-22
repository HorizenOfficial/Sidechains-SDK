package com.horizen.account.sc2sc

import com.horizen.account.fixtures.AccountCrossChainMessageFixture
import com.horizen.account.sc2sc.CrossChainRedeemMessageProcessorImpl.{contractAddress, nextScCommitmentTreeRoot, receiverSidechain, scCommitmentTreeRoot}
import com.horizen.account.state._
import com.horizen.cryptolibprovider.Sc2scCircuit
import com.horizen.evm.utils.Address
import com.horizen.params.NetworkParams
import com.horizen.sc2sc.CrossChainMessageHash
import com.horizen.utils.BytesUtils
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
  private val crossChainMessageHash = mock[CrossChainMessageHash]

  @Test
  def whenReceivingScIdIsDifferentThenTheScIdInSettings_throwsAnIllegalArgumentException(): Unit = {
    // Arrange
    val ccRedeemMsgProcessor: AbstractCrossChainRedeemMessageProcessor = new CrossChainRedeemMessageProcessorImpl(mockStateView, networkParamsMock, sc2scCircuitMock)
    val msg = mock[Message]

    val badScId = "badSidechainId".getBytes
    when(networkParamsMock.sidechainId).thenReturn(badScId)

    // Act
    val exception = intercept[IllegalArgumentException] {
      withGas(ccRedeemMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    }

    // Assert
    val expectedMsg = s"This scId `${BytesUtils.toHexString(badScId)}` and receiving scId `${BytesUtils.toHexString(receiverSidechain)}` do not match"
    assertEquals(expectedMsg, exception.getMessage)
  }

  @Test
  def whenTryToRedeemTheSameMessageTwice_throwsAnIllegalArgumentException(): Unit = {
    // Arrange
    val ccRedeemMsgProcessor: AbstractCrossChainRedeemMessageProcessor = new CrossChainRedeemMessageProcessorImpl(mockStateView, networkParamsMock, sc2scCircuitMock)
    val msg = mock[Message]

    when(networkParamsMock.sidechainId).thenReturn(receiverSidechain)

    when(sc2scCircuitMock.getCrossChainMessageHash(any())).thenReturn(crossChainMessageHash)
    when(crossChainMessageHash.getValue).thenReturn("someBytes".getBytes)
    val ccMsgHash = ccRedeemMsgProcessor.getCrossChainMessageFromRedeemKey(crossChainMessageHash.getValue)
    when(mockStateView.getAccountStorage(contractAddress, ccMsgHash)).thenReturn("someBytes".getBytes)

    // Act
    val exception = intercept[IllegalArgumentException] {
      withGas(ccRedeemMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    }

    // Assert
    val expectedMsg = s" has already been redeemed"
    assertTrue(exception.getMessage.contains(expectedMsg))
  }

  @Test
  def whenScTxCommitmentTreeHashDoesNotExist_throwsAnIllegalArgumentException(): Unit = {
    // Arrange
    val ccRedeemMsgProcessor: AbstractCrossChainRedeemMessageProcessor = new CrossChainRedeemMessageProcessorImpl(mockStateView, networkParamsMock, sc2scCircuitMock)
    val msg = mock[Message]

    when(networkParamsMock.sidechainId).thenReturn(receiverSidechain)

    when(sc2scCircuitMock.getCrossChainMessageHash(any())).thenReturn(crossChainMessageHash)
    when(crossChainMessageHash.getValue).thenReturn("someBytes".getBytes)
    val ccMsgHash = ccRedeemMsgProcessor.getCrossChainMessageFromRedeemKey(crossChainMessageHash.getValue)
    when(mockStateView.getAccountStorage(contractAddress, ccMsgHash)).thenReturn(Array.emptyByteArray)

    val scTxKey = ccRedeemMsgProcessor.getScTxCommitmentTreeRootHashKey(scCommitmentTreeRoot)
    when(mockStateView.getAccountStorage(contractAddress, scTxKey)).thenReturn(Array.emptyByteArray)

    // Act
    val exception = intercept[IllegalArgumentException] {
      withGas(ccRedeemMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    }

    // Assert
    val expectedMsg = s"Sidechain commitment tree root `${BytesUtils.toHexString("scCommitmentTreeRoot".getBytes)}` does not exist"
    assertEquals(expectedMsg, exception.getMessage)
  }

  @Test
  def whenScNextTxCommitmentTreeHashDoesNotExist_throwsAnIllegalArgumentException(): Unit = {
    // Arrange
    val ccRedeemMsgProcessor: AbstractCrossChainRedeemMessageProcessor = new CrossChainRedeemMessageProcessorImpl(mockStateView, networkParamsMock, sc2scCircuitMock)
    val msg = mock[Message]

    when(networkParamsMock.sidechainId).thenReturn(receiverSidechain)

    when(sc2scCircuitMock.getCrossChainMessageHash(any())).thenReturn(crossChainMessageHash)
    when(crossChainMessageHash.getValue).thenReturn("someBytes".getBytes)
    val ccMsgHash = ccRedeemMsgProcessor.getCrossChainMessageFromRedeemKey(crossChainMessageHash.getValue)
    when(mockStateView.getAccountStorage(contractAddress, ccMsgHash)).thenReturn(Array.emptyByteArray)

    val scTxKey = ccRedeemMsgProcessor.getScTxCommitmentTreeRootHashKey(scCommitmentTreeRoot)
    when(mockStateView.getAccountStorage(contractAddress, scTxKey)).thenReturn("someBytes".getBytes)
    val nextScTxKey = ccRedeemMsgProcessor.getScTxCommitmentTreeRootHashKey(nextScCommitmentTreeRoot)
    when(mockStateView.getAccountStorage(contractAddress, nextScTxKey)).thenReturn(Array.emptyByteArray)

    // Act
    val exception = intercept[IllegalArgumentException] {
      withGas(ccRedeemMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    }

    // Assert
    val expectedMsg = s"Sidechain next commitment tree root `${BytesUtils.toHexString("nextScCommitmentTreeRoot".getBytes)}` does not exist"
    assertEquals(expectedMsg, exception.getMessage)
  }

  @Test
  def whenProofCannotBeVerified_throwsAnIllegalArgumentException(): Unit = {
    // Arrange
    val ccRedeemMsgProcessor: AbstractCrossChainRedeemMessageProcessor = new CrossChainRedeemMessageProcessorImpl(mockStateView, networkParamsMock, sc2scCircuitMock)
    val msg = mock[Message]

    when(networkParamsMock.sidechainId).thenReturn(receiverSidechain)

    when(sc2scCircuitMock.getCrossChainMessageHash(any())).thenReturn(crossChainMessageHash)
    when(crossChainMessageHash.getValue).thenReturn("someBytes".getBytes)
    val ccMsgHash = ccRedeemMsgProcessor.getCrossChainMessageFromRedeemKey(crossChainMessageHash.getValue)
    when(mockStateView.getAccountStorage(contractAddress, ccMsgHash)).thenReturn(Array.emptyByteArray)

    val scTxKey = ccRedeemMsgProcessor.getScTxCommitmentTreeRootHashKey(scCommitmentTreeRoot)
    when(mockStateView.getAccountStorage(contractAddress, scTxKey)).thenReturn("someBytes".getBytes)
    val nextScTxKey = ccRedeemMsgProcessor.getScTxCommitmentTreeRootHashKey(nextScCommitmentTreeRoot)
    when(mockStateView.getAccountStorage(contractAddress, nextScTxKey)).thenReturn("someBytes".getBytes)

    when(sc2scCircuitMock.getCrossChainMessageHash(any())).thenReturn(crossChainMessageHash)
    when(sc2scCircuitMock.verifyRedeemProof(ArgumentMatchers.eq(crossChainMessageHash), any(), any(), any(), any(), any())).thenReturn(false)

    // Act
    val exception = intercept[IllegalArgumentException] {
      withGas(ccRedeemMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    }

    // Assert
    val expectedMsg = s"Cannot verify this cross-chain message"
    assertTrue(exception.getMessage.contains(expectedMsg))
  }

  @Test
  def whenAllValidationsPass_throwsNoException(): Unit = {
    // Arrange
    val ccRedeemMsgProcessor: AbstractCrossChainRedeemMessageProcessor = new CrossChainRedeemMessageProcessorImpl(mockStateView, networkParamsMock, sc2scCircuitMock)
    val msg = mock[Message]

    when(networkParamsMock.sidechainId).thenReturn(receiverSidechain)

    when(sc2scCircuitMock.getCrossChainMessageHash(any())).thenReturn(crossChainMessageHash)
    when(crossChainMessageHash.getValue).thenReturn("someBytes".getBytes)
    val ccMsgHash = ccRedeemMsgProcessor.getCrossChainMessageFromRedeemKey(crossChainMessageHash.getValue)
    when(mockStateView.getAccountStorage(contractAddress, ccMsgHash)).thenReturn(Array.emptyByteArray)

    val scTxKey = ccRedeemMsgProcessor.getScTxCommitmentTreeRootHashKey(scCommitmentTreeRoot)
    when(mockStateView.getAccountStorage(contractAddress, scTxKey)).thenReturn("someBytes".getBytes)
    val nextScTxKey = ccRedeemMsgProcessor.getScTxCommitmentTreeRootHashKey(nextScCommitmentTreeRoot)
    when(mockStateView.getAccountStorage(contractAddress, nextScTxKey)).thenReturn("someBytes".getBytes)

    when(sc2scCircuitMock.getCrossChainMessageHash(any())).thenReturn(crossChainMessageHash)
    when(sc2scCircuitMock.verifyRedeemProof(ArgumentMatchers.eq(crossChainMessageHash), any(), any(), any(), any(), any())).thenReturn(true)

    // Act & Assert
    try withGas(ccRedeemMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    catch {
      case _: Exception =>
        fail("Test failed unexpectedly")
    }
  }
}


class CrossChainRedeemMessageProcessorImpl(accountStateView: AccountStateView, networkParams: NetworkParams, sc2scCircuit: Sc2scCircuit)
  extends AbstractCrossChainRedeemMessageProcessor(networkParams, sc2scCircuit) {

  override val contractAddress: Address = CrossChainRedeemMessageProcessorImpl.contractAddress
  override val contractCode: Array[Byte] = CrossChainRedeemMessageProcessorImpl.contractCode

  override protected def getAccountCrossChainRedeemMessageFromMessage(msg: Message): AccountCrossChainRedeemMessage = {
    val accountCrossChainMessage = AccountCrossChainMessage(
      messageType = 1,
      sender = "d504dbfde192182c68d2bcec6e452049".getBytes,
      receiverSidechain = receiverSidechain,
      receiver = "0303908acce9dd1078bdf16a87a9d9f8".getBytes,
      payload = "my payload".getBytes,
    )
    val certificateDataHash = "certificateDataHash".getBytes
    val nextCertificateDataHash = "nextCertificateDataHash".getBytes
    val scCommitmentTreeRoot = CrossChainRedeemMessageProcessorImpl.scCommitmentTreeRoot
    val nextScCommitmentTreeRoot = CrossChainRedeemMessageProcessorImpl.nextScCommitmentTreeRoot
    val proof = "proof".getBytes
    AccountCrossChainRedeemMessage(
      accountCrossChainMessage, certificateDataHash, nextCertificateDataHash, scCommitmentTreeRoot, nextScCommitmentTreeRoot, proof
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

  val receiverSidechain: Array[Byte] = "receiverSidechain".getBytes
  val scCommitmentTreeRoot: Array[Byte] = "scCommitmentTreeRoot".getBytes
  val nextScCommitmentTreeRoot: Array[Byte] = "nextScCommitmentTreeRoot".getBytes
}