package io.horizen.utxo.network

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import io.horizen._
import io.horizen.block.SidechainBlockBase
import io.horizen.utxo.companion.SidechainTransactionsCompanion
import io.horizen.fixtures.SidechainBlockFixture.{getDefaultTransactionsCompanion, sidechainTransactionsCompanion}
import io.horizen.fixtures.SidechainBlockInfoFixture
import io.horizen.utils.BytesUtils
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockSerializer}
import io.horizen.utxo.transaction.{RegularTransaction, RegularTransactionSerializer}
import io.horizen.history.validation.{BlockInFutureException, InconsistentDataException, InvalidBlockException, InvalidSidechainBlockHeaderException}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{After, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import sparkz.core.NodeViewHolder.ReceivableMessages.{GetNodeViewChanges, ModifiersFromRemote, TransactionsFromRemote}
import sparkz.core.network.ModifiersStatus.{Held, Requested}
import sparkz.core.network.NetworkController.ReceivableMessages.{PenalizePeer, RegisterMessageSpecs, StartConnectingPeers}
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.SyntacticallyFailedModification
import sparkz.core.network.message.{Message, MessageSerializer, ModifiersData, ModifiersSpec}
import sparkz.core.network.{ConnectedPeer, ConnectionId, DeliveryTracker, Incoming}
import sparkz.core.serialization.SparkzSerializer
import sparkz.core.settings.SparkzSettings
import sparkz.core.transaction.Transaction
import sparkz.core.utils.NetworkTimeProvider
import sparkz.core.{ModifierTypeId, NodeViewModifier}
import sparkz.util.ModifierId

import java.io.{BufferedReader, FileReader}
import java.net.InetSocketAddress
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Promise}
import scala.language.postfixOps
import scala.util.Success

class SidechainNodeViewSynchronizerTest extends JUnitSuite
  with MockitoSugar
  with SidechainBlockInfoFixture {

  implicit val actorSystem: ActorSystem = ActorSystem("sc_nvhs_mocked")
  implicit val executionContext: ExecutionContext = actorSystem.dispatchers.lookup("sparkz.executionContext")

  private val modifiersSpec = new ModifiersSpec(1024 * 1024)

  val (nodeViewSynchronizerRef, deliveryTracker, block, peer, networkControllerProbe, viewHolderProbe, messageSerializer) = prepareData()

  @Test
  def onSyntacticallyFailedModification(): Unit = {
    var setUnknownExecuted: Boolean = false


    // Test 1: BlockInFutureException exception processing
    val blockInFutureException = new BlockInFutureException("block in future exception")

    val promise = Promise[Unit]()
    Mockito.when(deliveryTracker.setUnknown(ArgumentMatchers.any[ModifierId])).thenAnswer(answer => {
      val receivedId: ModifierId = answer.getArgument(0)
      assertEquals("Different block id expected.", block.id, receivedId)
      setUnknownExecuted = true
      promise.success(Unit)
    })

    nodeViewSynchronizerRef ! SyntacticallyFailedModification(block, blockInFutureException)

    Await.result(promise.future, 2 seconds)

    // Check that sender was not penalize
    networkControllerProbe.expectNoMessage()
    // Check that block was set to Unknown -> no ban
    assertTrue("Delivery tracker expected to set block id as Unknown.", setUnknownExecuted)



    // Test 2: InconsistentDataException exception processing
    setUnknownExecuted = false
    val inconsistentDataException = new InconsistentDataException("inconsistent data exception")

    Mockito.reset(deliveryTracker)
    Mockito.when(deliveryTracker.peerInfo(ArgumentMatchers.any[ModifierId])).thenAnswer(answer => {
      val receivedId: ModifierId = answer.getArgument(0)
      assertEquals("Different block id expected.", block.id, receivedId)
      Some(peer)
    })

    Mockito.when(deliveryTracker.setUnknown(ArgumentMatchers.any[ModifierId])).thenAnswer(answer => {
      val receivedId: ModifierId = answer.getArgument(0)
      assertEquals("Different block id expected.", block.id, receivedId)
      setUnknownExecuted = true
    })

    nodeViewSynchronizerRef ! SyntacticallyFailedModification(block, inconsistentDataException)

    // Check that sender was penalize
    networkControllerProbe.expectMsgType[PenalizePeer]
    // Check that block was set to Unknown -> no ban
    assertTrue("Delivery tracker expected to set block id as Unknown.", setUnknownExecuted)


    // Test 3: Any other exception processing
    var setInvalidExecuted = false
    Mockito.reset(deliveryTracker)
    Mockito.when(deliveryTracker.setInvalid(ArgumentMatchers.any[ModifierId])).thenAnswer(answer => {
      val receivedId: ModifierId = answer.getArgument(0)
      assertEquals("Different block id expected.", block.id, receivedId)
      setInvalidExecuted = true
      Some(peer)
    })

    // Test on InvalidSidechainBlockHeaderException
    val sidechainBlockHeaderInvalidException = new InvalidSidechainBlockHeaderException("block header invalid exception")
    nodeViewSynchronizerRef ! SyntacticallyFailedModification(block, sidechainBlockHeaderInvalidException)
    // Check that sender was penalize
    networkControllerProbe.expectMsgType[PenalizePeer]
    // Check that block was set to Invalid -> ban
    assertTrue("Delivery tracker expected to set block id as Invalid.", setInvalidExecuted)

    // Test on InvalidBlockException
    setInvalidExecuted = false
    val invalidDataException = new InvalidBlockException("invalid data exception")
    nodeViewSynchronizerRef ! SyntacticallyFailedModification(block, invalidDataException)
    // Check that sender was penalize
    networkControllerProbe.expectMsgType[PenalizePeer]
    // Check that block was set to Invalid -> ban
    assertTrue("Delivery tracker expected to set block id as Invalid.", setInvalidExecuted)

    // Test on IllegalArgumentException
    setInvalidExecuted = false
    val otherException = new IllegalArgumentException("other exception")
    nodeViewSynchronizerRef ! SyntacticallyFailedModification(block, otherException)
    // Check that sender was penalize
    networkControllerProbe.expectMsgType[PenalizePeer]
    // Check that block was set to Invalid -> ban
    assertTrue("Delivery tracker expected to set block id as Invalid.", setInvalidExecuted)
  }

  @Test
  def onAdditionalTransactionBytes(): Unit = {
    val classLoader = getClass.getClassLoader
    val file = new FileReader(classLoader.getResource("regulartransaction_hex").getFile)
    val transactionHexBytes = BytesUtils.fromHexString(new BufferedReader(file).readLine())

    val additianalBytes: Array[Byte] = Array(0x00, 0x0a, 0x01, 0x0b)
    val regularTransactionSerializer = RegularTransactionSerializer.getSerializer

    val deserializedTransactionTry = regularTransactionSerializer.parseBytesTry(transactionHexBytes)
    assertTrue("Cannot deserialize original Sidechain transaction", deserializedTransactionTry.isSuccess)
    val originalTransaction = deserializedTransactionTry.get

    val transactionBytes = sidechainTransactionsCompanion.toBytes(originalTransaction)
    val transferData = transactionBytes ++ additianalBytes

    Mockito.reset(deliveryTracker)
    Mockito.when(deliveryTracker.status(ArgumentMatchers.any[ModifierId])).thenAnswer(answer => {
      val receivedId: ModifierId = answer.getArgument(0)
      assertEquals("Different transaction id expected.", originalTransaction.id, receivedId)
      Requested
    })

    nodeViewSynchronizerRef ! roundTrip(Message(modifiersSpec, Right(ModifiersData(Transaction.ModifierTypeId, Seq(ModifierId @@ originalTransaction.id -> transactionBytes))), Some(peer)))
    viewHolderProbe.expectMsgType[TransactionsFromRemote[RegularTransaction]]
    networkControllerProbe.expectNoMessage()

    nodeViewSynchronizerRef ! roundTrip(Message(modifiersSpec, Right(ModifiersData(Transaction.ModifierTypeId, Seq(ModifierId @@ originalTransaction.id -> transferData))), Some(peer)))
    viewHolderProbe.expectMsgType[TransactionsFromRemote[RegularTransaction]]
    // Check that sender was penalize
    networkControllerProbe.expectMsgType[PenalizePeer]
  }

  @Test
  def onAdditionalBlockBytes(): Unit = {
    val classLoader = getClass.getClassLoader
    val file = new FileReader(classLoader.getResource("sidechainblock_hex").getFile)
    val blockBytes = BytesUtils.fromHexString(new BufferedReader(file).readLine())

    val additianalBytes: Array[Byte] = Array(0x00, 0x0a, 0x01, 0x0b)
    val transferData = blockBytes ++ additianalBytes

    val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion
    val sidechainBlockSerializer = new SidechainBlockSerializer(sidechainTransactionsCompanion)

    val deserializedBlockTry = sidechainBlockSerializer.parseBytesTry(blockBytes)
    assertTrue("Cannot deserialize original SidechainBlock", deserializedBlockTry.isSuccess)
    val deserializedBlock = deserializedBlockTry.get

    Mockito.reset(deliveryTracker)
    Mockito.when(deliveryTracker.status(ArgumentMatchers.any[ModifierId])).thenAnswer(answer => {
      val receivedId: ModifierId = answer.getArgument(0)
      if (receivedId == deserializedBlock.parentId) Held
      else if (receivedId == deserializedBlock.id) Requested
      else fail("Different block id expected.")
    })

    nodeViewSynchronizerRef ! roundTrip(Message(modifiersSpec, Right(ModifiersData(SidechainBlockBase.ModifierTypeId, Seq(deserializedBlock.id -> blockBytes))), Some(peer)))
    viewHolderProbe.expectMsgType[ModifiersFromRemote[SidechainBlock]]
    networkControllerProbe.expectNoMessage()

    nodeViewSynchronizerRef ! roundTrip(Message(modifiersSpec, Right(ModifiersData(SidechainBlockBase.ModifierTypeId, Seq(deserializedBlock.id -> transferData))), Some(peer)))
    viewHolderProbe.expectMsgType[ModifiersFromRemote[SidechainBlock]]
    // Check that sender was penalize
    networkControllerProbe.expectMsgType[PenalizePeer]
  }

  @After
  def afterAll(): Unit = {
    actorSystem.terminate()
  }

  def roundTrip(msg: Message[_]): Message[_] = {
    messageSerializer.deserialize(messageSerializer.serialize(msg), msg.source) match {
      case Success(Some(value)) => value
    }
  }

  protected def prepareData(): (ActorRef, DeliveryTracker, SidechainBlock, ConnectedPeer, TestProbe, TestProbe, MessageSerializer) = {
    val networkControllerProbe = TestProbe()
    val viewHolderProbe = TestProbe()
    val sparkzSettings: SparkzSettings = SparkzSettings.read(Some(getClass.getClassLoader.getResource("sc_node_holder_fixter_settings.conf").getFile))
    val timeProvider = new NetworkTimeProvider(sparkzSettings.ntp)

    val peer = ConnectedPeer(ConnectionId(new InetSocketAddress(10), new InetSocketAddress(11), Incoming), mock[ActorRef], 0L, None)
    val tracker: DeliveryTracker = mock[DeliveryTracker]

    val modifierSerializers: Map[ModifierTypeId, SparkzSerializer[_ <: NodeViewModifier]] =
    Map(SidechainBlockBase.ModifierTypeId -> new SidechainBlockSerializer(sidechainTransactionsCompanion),
      Transaction.ModifierTypeId -> sidechainTransactionsCompanion)

    val nodeViewSynchronizerRef = actorSystem.actorOf(Props(
      new SidechainNodeViewSynchronizer(networkControllerProbe.ref, viewHolderProbe.ref, SidechainSyncInfoMessageSpec, sparkzSettings.network, timeProvider, modifierSerializers) {
        override protected val deliveryTracker: DeliveryTracker = tracker
      }))

    networkControllerProbe.expectMsgType[RegisterMessageSpecs]
    networkControllerProbe.expectMsgType[StartConnectingPeers.type]
    viewHolderProbe.expectMsgType[GetNodeViewChanges]

    val modifierId: ModifierId = getRandomModifier()
    val block = mock[SidechainBlock]
    Mockito.when(block.id).thenReturn(modifierId)

    val messageSerializer = new MessageSerializer(Seq(modifiersSpec), sparkzSettings.network.magicBytes, sparkzSettings.network.messageLengthBytesLimit)

    (nodeViewSynchronizerRef, tracker, block, peer, networkControllerProbe, viewHolderProbe, messageSerializer)
  }
}
