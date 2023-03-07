package io.horizen.network

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import io.horizen._
import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.chain.{AbstractFeePaymentsInfo, SidechainBlockInfo}
import io.horizen.history.AbstractHistory
import io.horizen.network.SyncStatusActor.ReceivableMessages.{NotifySyncStart, NotifySyncStop, ReturnSyncStatus}
import io.horizen.params.NetworkParams
import io.horizen.storage.AbstractHistoryStorage
import io.horizen.transaction.Transaction
import io.horizen.wallet.Wallet
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import sparkz.core.network.NodeViewSynchronizer.Events.{BetterNeighbourAppeared, NoBetterNeighbour, NodeViewSynchronizerEvent}
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.SemanticallySuccessfulModifier
import sparkz.core.transaction.MemoryPool
import sparkz.core.utils.NetworkTimeProvider
import sparkz.util.SparkzLogging

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext}

class SyncStatusActor[
  TX <: Transaction, H <: SidechainBlockHeaderBase,
  PMOD <: SidechainBlockBase[TX, H],
  SI <: SidechainSyncInfo,
  FPI <: AbstractFeePaymentsInfo,
  HSTOR <: AbstractHistoryStorage[PMOD, FPI, HSTOR],
  HIS <: AbstractHistory[TX, H, PMOD, FPI, HSTOR, HIS],
  MS <: AbstractState[TX, H, PMOD, MS],
  VL <: Wallet[SidechainTypes#SCS, SidechainTypes#SCP, TX, PMOD, VL],
  MP <: MemoryPool[TX, MP]
]
(
  settings: SidechainSettings,
  sidechainNodeViewHolderRef: ActorRef,
  params: NetworkParams,
  timeProvider: NetworkTimeProvider
)
  extends Actor with SparkzLogging {

  type View = CurrentView[HIS, MS, VL, MP]

  private var syncStatus: Boolean = _
  private var processNewBlocks: Boolean = true

  private val confidenceParameter: Int = 2
  private val previousBlocksRange: Int = 100

  var currentBlock: Int = _
  var startingBlock: Int = _
  var highestBlock: Int = _

  lazy val timeoutDuration: FiniteDuration = settings.sparkzSettings.restApi.timeout
  implicit lazy val timeout: Timeout = Timeout(timeoutDuration)

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[SemanticallySuccessfulModifier[PMOD]])
    context.system.eventStream.subscribe(self, classOf[NodeViewSynchronizerEvent])
  }

  override def postStop(): Unit = {
    log.debug("SyncStatusActor is stopping...")
    super.postStop()
  }

  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
    log.error("SyncStatusActor was restarted because of: ", reason)
  }

  def retrieveCurrentHeight(sidechainNodeView: View): Int = {
    sidechainNodeView.history.getCurrentHeight
  }

  def retrieveBlockInfoByHeight(sidechainNodeView: View, blockHeight: Int): SidechainBlockInfo = {
    val pastBlockId = sidechainNodeView.history.getBlockIdByHeight(blockHeight).get()
    val pastBlockInfo = sidechainNodeView.history.getBlockInfoById(pastBlockId).get()
    pastBlockInfo
  }

  // return true if the difference between current timestamp and applied block timestamp is less than block rate * confidence parameter
  // return false otherwise
  def checkAppliedBlockTimestamp(blockTimestamp: Long): Boolean = {
    (timeProvider.time() - blockTimestamp).compareTo(params.consensusSecondsInSlot * confidenceParameter) < 0
  }

  // In this method the message SemanticallySuccessfulModifier is processed
  // this message is sent when a new block is applied to the state
  // the new blocks are processed in the sync status actor if the processNewBlocks is active
  // if the syncStatus we retrieve the current block from the view and calculate the potential highest block (given the current timestamp)
  // otherwise the currentBlock counter is updated
  // if the syncStatus is true and the applied block timestamp is near the current timestamp (block rate * confidence parameter)
  // we set the node as synched and reset the internal state
  def processNewBlockApplied(sidechainBlock: PMOD): Unit = {

    if(processNewBlocks) {

      // if the node is not (yet) syncing we process the new block and update the syncStatus
      if (!syncStatus) {

        // retrieve the current block height from the node view
        val currentHeightFromView = Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(retrieveCurrentHeight), timeoutDuration)
          .asInstanceOf[Int]

        // calculate the block correction needed to calculate the potential highest block
        val blockCorrection = Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView((view: View) =>
          calculateBlockCorrection(view, currentHeightFromView)), timeoutDuration)
          .asInstanceOf[Double]

        // calculate the potential highest block
        val currentTime: Long = timeProvider.time() / 1000
        val timestampDifference = currentTime - sidechainBlock.timestamp
        val blocksFromCurrentToTip = ((timestampDifference / params.consensusSecondsInSlot.toDouble) * blockCorrection).toInt

        // set the current internal state
        syncStatus = true
        currentBlock = currentHeightFromView
        startingBlock = currentHeightFromView
        highestBlock = currentHeightFromView + blocksFromCurrentToTip

        // broadcast sync start and its details, starting block is equal to current block in this case
        val syncStatusMessage = new SyncStatus(true, BigInt(currentBlock), BigInt(startingBlock), BigInt(highestBlock))
        context.system.eventStream.publish(NotifySyncStart(syncStatusMessage))

      }
      // if the node is already syncing we update the current block value adding one for every new block that is applied on the state
      // we also check if the applied block timestamp is near the current timestamp, in this case we set the syncStatus to false and reset the state
      else {
        // update the current block counter
        currentBlock += 1
        if (checkAppliedBlockTimestamp(sidechainBlock.timestamp)) {
          // reset the internal state
          syncStatus = false; processNewBlocks = false
          currentBlock = 0; startingBlock = 0; highestBlock = 0
          // broadcast sync stop
          context.system.eventStream.publish(NotifySyncStop())
        }
      }

    }

  }

  // Calculate the block correction parameter retrieving the last n blocks (n = previousBlocksRange) and check how many slots were occupied
  // if the previousBlocksRange value is less than the number of blocks present in the node history we just return the value 1
  // this previous case can be encounter at node first startup without blocks in history
  def calculateBlockCorrection(sidechainNodeView: View, currentBlockHeight: Int): Double = {
    if (previousBlocksRange.compare(currentBlockHeight) < 0) {
      val pastBlockInfo = Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView((view: View) =>
        retrieveBlockInfoByHeight(view, currentBlockHeight - previousBlocksRange)), timeoutDuration)
        .asInstanceOf[SidechainBlockInfo]
      val timestampDifference = timeProvider.time() - pastBlockInfo.timestamp
      val expectedBlocksInRange = timestampDifference / params.consensusSecondsInSlot.toDouble
      val slotCorrection = previousBlocksRange / expectedBlocksInRange
      slotCorrection
    } else 1
  }

  // In this method the message NoBetterNeighbour is processed
  // if the syncStatus is false (the node is not syncing) do nothing
  // otherwise reset all the internal actor values and set the syncStatus and processNewBlocks flags to False (the node
  // is currently in a stable sync regime)
  def processNoBetterNeighbour(): Unit = {
    if(syncStatus) {
      syncStatus = false; processNewBlocks = false
      currentBlock = 0; startingBlock = 0; highestBlock = 0
    }
  }

  // In this method the message BetterNeighbourAppeared is processed
  // set the processNewBlocks flag to true, the actor is able to process new blocks applied to the state
  def processBetterNeighbour(): Unit = {
    processNewBlocks = true
  }

  protected def processSidechainNodeViewHolderEvents: Receive = {

    case SemanticallySuccessfulModifier(sidechainBlock: PMOD) =>
      processNewBlockApplied(sidechainBlock)

  }

  protected def processSyncTrackerEvents: Receive = {

    case NoBetterNeighbour =>
      processNoBetterNeighbour()

    case BetterNeighbourAppeared =>
      processBetterNeighbour()
  }

  // used at rpc level (eth_syncing method) to retrieve updated information on the sync status of the node
  protected def returnSyncStatus: Receive = {
    case ReturnSyncStatus() =>
      if (syncStatus) {
        // retrieve current block height from view
        val currentHeightFromView = Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(retrieveCurrentHeight), timeoutDuration)
          .asInstanceOf[Int]
        sender() ! new SyncStatus(syncStatus, currentHeightFromView, startingBlock, highestBlock)
      } else {
        sender() ! new SyncStatus(syncStatus)
      }
  }

  override def receive: Receive = {
    processSidechainNodeViewHolderEvents orElse
      processSyncTrackerEvents orElse
      returnSyncStatus orElse {
      case message: Any => log.error("SyncStatusActor received strange message: " + message.getClass.toString)
    }
  }

}

object SyncStatusActor {

  object ReceivableMessages {

    case class ReturnSyncStatus()

    case class NotifySyncStart(syncStatus: SyncStatus)

    case class NotifySyncStop()

  }

}

object SyncStatusActorRef {
  def props[
    TX <: Transaction, H <: SidechainBlockHeaderBase,
    PMOD <: SidechainBlockBase[TX, H],
    SI <: SidechainSyncInfo,
    FPI <: AbstractFeePaymentsInfo,
    HSTOR <: AbstractHistoryStorage[PMOD, FPI, HSTOR],
    HIS <: AbstractHistory[TX, H, PMOD, FPI, HSTOR, HIS],
    MS <: AbstractState[TX, H, PMOD, MS],
    VL <: Wallet[SidechainTypes#SCS, SidechainTypes#SCP, TX, PMOD, VL],
    MP <: MemoryPool[TX, MP]
  ](settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams, timeProvider: NetworkTimeProvider)
   (implicit ec: ExecutionContext): Props =
    Props(new SyncStatusActor[TX, H, PMOD, SI, FPI, HSTOR, HIS, MS, VL, MP](settings, sidechainNodeViewHolderRef, params, timeProvider))

  def apply[
    TX <: Transaction, H <: SidechainBlockHeaderBase,
    PMOD <: SidechainBlockBase[TX, H],
    SI <: SidechainSyncInfo,
    FPI <: AbstractFeePaymentsInfo,
    HSTOR <: AbstractHistoryStorage[PMOD, FPI, HSTOR],
    HIS <: AbstractHistory[TX, H, PMOD, FPI, HSTOR, HIS],
    MS <: AbstractState[TX, H, PMOD, MS],
    VL <: Wallet[SidechainTypes#SCS, SidechainTypes#SCP, TX, PMOD, VL],
    MP <: MemoryPool[TX, MP]
  ](name: String, settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams, timeProvider: NetworkTimeProvider)
   (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props[TX, H, PMOD, SI, FPI, HSTOR, HIS, MS, VL, MP](settings, sidechainNodeViewHolderRef, params, timeProvider), name)

  def apply[
    TX <: Transaction, H <: SidechainBlockHeaderBase,
    PMOD <: SidechainBlockBase[TX, H],
    SI <: SidechainSyncInfo,
    FPI <: AbstractFeePaymentsInfo,
    HSTOR <: AbstractHistoryStorage[PMOD, FPI, HSTOR],
    HIS <: AbstractHistory[TX, H, PMOD, FPI, HSTOR, HIS],
    MS <: AbstractState[TX, H, PMOD, MS],
    VL <: Wallet[SidechainTypes#SCS, SidechainTypes#SCP, TX, PMOD, VL],
    MP <: MemoryPool[TX, MP]
  ](settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams, timeProvider: NetworkTimeProvider)
   (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props[TX, H, PMOD, SI, FPI, HSTOR, HIS, MS, VL, MP](settings, sidechainNodeViewHolderRef, params, timeProvider))
}

