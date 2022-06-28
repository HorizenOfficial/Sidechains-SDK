package com.horizen.network

import java.io.{BufferedReader, FileReader}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import com.horizen._
import com.horizen.block.{SidechainBlock, SidechainBlockSerializer}
import com.horizen.fixtures.SidechainBlockInfoFixture
import com.horizen.validation.{BlockInFutureException, InconsistentDataException, InvalidBlockException, InvalidSidechainBlockHeaderException}
import org.junit.{After, Ignore, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.SyntacticallyFailedModification
import scorex.core.settings.ScorexSettings
import scorex.core.utils.NetworkTimeProvider
import scorex.util.ModifierId
import org.junit.Assert.{assertEquals, assertTrue}
import scorex.core.network.{ConnectedPeer, ConnectionId, Incoming}
import scorex.core.network.NetworkController.ReceivableMessages.{PenalizePeer, RegisterMessageSpecs}
import scorex.core.network.NetworkControllerSharedMessages.ReceivableMessages.DataFromPeer
import scorex.core.network.message.{ModifiersData, ModifiersSpec, RequestModifierSpec}
import java.net.InetSocketAddress

import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.fixtures.SidechainBlockFixture.{getDefaultTransactionsCompanion, sidechainTransactionsCompanion}
import com.horizen.transaction.{RegularTransaction, RegularTransactionSerializer}
import com.horizen.utils.BytesUtils
import scorex.core.{ModifierTypeId, NodeViewModifier}
import scorex.core.NodeViewHolder.ReceivableMessages.{GetNodeViewChanges, ModifiersFromRemote, TransactionsFromRemote}
import scorex.core.network.ModifiersStatus.Requested
import scorex.core.serialization.ScorexSerializer
import scorex.core.transaction.Transaction

import scala.collection.immutable.Map
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Promise}
import scala.language.postfixOps

class SidechainNodeViewSynchronizerTest extends JUnitSuite
  with MockitoSugar
  with SidechainBlockInfoFixture {

  implicit val actorSystem: ActorSystem = ActorSystem("sc_nvhs_mocked")
  implicit val executionContext: ExecutionContext = actorSystem.dispatchers.lookup("scorex.executionContext")

  val (nodeViewSynchronizerRef, deliveryTracker, block, peer, networkControllerProbe, viewHolderProbe) = prepareData()

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
  def onAdditianalTransactionBytes(): Unit = {
    val modifiersSpec = new ModifiersSpec(1024 * 1024)

    val classLoader = getClass.getClassLoader
    val file = new FileReader(classLoader.getResource("regulartransaction_hex").getFile)
    val transactionHexBytes = BytesUtils.fromHexString(new BufferedReader(file).readLine())

    val additianalBytes: Array[Byte] = Array(0x00, 0x0a, 0x01, 0x0b)
    val regularTransactionSerializer = RegularTransactionSerializer.getSerializer()

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

    nodeViewSynchronizerRef ! DataFromPeer(modifiersSpec, ModifiersData(Transaction.ModifierTypeId, Map(ModifierId @@ originalTransaction.id -> transactionBytes)), peer)
    viewHolderProbe.expectMsgType[TransactionsFromRemote[RegularTransaction]]

    nodeViewSynchronizerRef ! DataFromPeer(modifiersSpec, ModifiersData(Transaction.ModifierTypeId, Map(ModifierId @@ originalTransaction.id -> transferData)), peer)
    viewHolderProbe.expectMsgType[TransactionsFromRemote[RegularTransaction]]
    // Check that sender was penalize
    networkControllerProbe.expectMsgType[PenalizePeer]
  }

  @Test
  def onAdditianalBlockBytes(): Unit = {
    val modifiersSpec = new ModifiersSpec(1024 * 1024)
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
      assertEquals("Different block id expected.", deserializedBlock.id, receivedId)
      Requested
    })

    nodeViewSynchronizerRef ! DataFromPeer(modifiersSpec, ModifiersData(SidechainBlock.ModifierTypeId, Map(deserializedBlock.id -> blockBytes)), peer)
    viewHolderProbe.expectMsgType[ModifiersFromRemote[SidechainBlock]]

    nodeViewSynchronizerRef ! DataFromPeer(modifiersSpec, ModifiersData(SidechainBlock.ModifierTypeId, Map(deserializedBlock.id -> transferData)), peer)
    viewHolderProbe.expectMsgType[ModifiersFromRemote[SidechainBlock]]
    // Check that sender was penalize
    networkControllerProbe.expectMsgType[PenalizePeer]
  }

  @After
  def afterAll(): Unit = {
    actorSystem.terminate()
  }


  protected def prepareData(): (ActorRef, SidechainDeliveryTracker, SidechainBlock, ConnectedPeer, TestProbe, TestProbe) = {
    val networkControllerProbe = TestProbe()
    val viewHolderProbe = TestProbe()
    val scorexSettings: ScorexSettings = ScorexSettings.read(Some(getClass.getClassLoader.getResource("sc_node_holder_fixter_settings.conf").getFile))
    val timeProvider = new NetworkTimeProvider(scorexSettings.ntp)

    val peer = ConnectedPeer(ConnectionId(new InetSocketAddress(10), new InetSocketAddress(11), Incoming), mock[ActorRef], None)
    val tracker: SidechainDeliveryTracker = mock[SidechainDeliveryTracker]

    val modifierSerializers: Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]] =
    Map(SidechainBlock.ModifierTypeId -> new SidechainBlockSerializer(sidechainTransactionsCompanion),
      Transaction.ModifierTypeId -> sidechainTransactionsCompanion)

    val nodeViewSynchronizerRef = actorSystem.actorOf(Props(
      new SidechainNodeViewSynchronizer(networkControllerProbe.ref, viewHolderProbe.ref, SidechainSyncInfoMessageSpec, scorexSettings.network, timeProvider, modifierSerializers) {
        override protected val deliveryTracker: SidechainDeliveryTracker = tracker
      }))

    networkControllerProbe.expectMsgType[RegisterMessageSpecs]
    viewHolderProbe.expectMsgType[GetNodeViewChanges]

    val modifierId: ModifierId = getRandomModifier()
    val block = mock[SidechainBlock]
    Mockito.when(block.id).thenReturn(modifierId)

    (nodeViewSynchronizerRef, tracker, block, peer, networkControllerProbe, viewHolderProbe)
  }
}
