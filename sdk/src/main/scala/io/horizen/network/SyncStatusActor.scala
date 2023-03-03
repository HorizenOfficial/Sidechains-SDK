package io.horizen.network

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
import akka.pattern.ask
import akka.util.Timeout
import io.horizen._
import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.chain.{AbstractFeePaymentsInfo, SidechainBlockInfo}
import io.horizen.history.AbstractHistory
import io.horizen.network.SyncStatusActor.ReceivableMessages.{CheckBlockTimestamps, NotifySyncStart, NotifySyncStop, ReturnSyncStatus}
import io.horizen.params.NetworkParams
import io.horizen.storage.AbstractHistoryStorage
import io.horizen.transaction.Transaction
import io.horizen.wallet.Wallet
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import sparkz.core.network.NodeViewSynchronizer.Events.{BetterNeighbourAppeared, NodeViewSynchronizerEvent}
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.SemanticallySuccessfulModifier
import sparkz.core.transaction.MemoryPool
import sparkz.core.utils.NetworkTimeProvider
import sparkz.util.SparkzLogging

import scala.concurrent.duration.{DurationInt, FiniteDuration}
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
(implicit ec: ExecutionContext) extends Actor with SparkzLogging {

  type View = CurrentView[HIS, MS, VL, MP]

  private var syncStatus: Boolean = _
  private var processNewBlocks: Boolean = true

  private val confidenceParameter: Int = 2
  private var genesisBlockTimestamp: Long = _

  private var currentBlock: Int = _
  private var startingBlock: Int = _
  private var highestBlock: Int = _

  lazy val timeoutDuration: FiniteDuration = settings.sparkzSettings.restApi.timeout
  implicit lazy val timeout: Timeout = Timeout(timeoutDuration)
  private val checkBlocksTimestampInterval: FiniteDuration = 5 seconds

  var checkBlockTimestampsScheduler: Cancellable = _
  var currentBlockTimestamp: Long = _
  var schedulerBlockTimestamp: Long = _
  var updateHighestBlockCalculationThreshold: Int = 20000
  var updateHighestBlockCounter: Int = _

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
    // subscribe to events after actor restart
    context.system.eventStream.subscribe(self, classOf[SemanticallySuccessfulModifier[PMOD]])
    context.system.eventStream.subscribe(self, classOf[NodeViewSynchronizerEvent])
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
    ((timeProvider.time() / 1000) - blockTimestamp).compareTo(params.consensusSecondsInSlot * confidenceParameter) < 0
  }

  // common method to reset the internal state
  private def resetInternalState(): Unit = {
    syncStatus = false; processNewBlocks = false
    currentBlock = 0; startingBlock = 0; highestBlock = 0
    currentBlockTimestamp = 0L; schedulerBlockTimestamp = 0L
  }

  // In this method the message SemanticallySuccessfulModifier is processed
  // this message is sent when a new block is applied to the state
  // the new blocks are processed in the sync status actor if the processNewBlocks is active
  // if the syncStatus we retrieve the current block from the view and calculate the estimated highest block (given the current timestamp)
  // otherwise the currentBlock counter is updated
  // if the syncStatus is true and the applied block timestamp is near the current timestamp (block rate * confidence parameter)
  // we set the node as synched and reset the internal state
  protected def processNewBlockApplied(sidechainBlock: PMOD): Unit = {

    if(processNewBlocks) {

      // if the node is not (yet) syncing we process the new block and update the syncStatus
      if (!syncStatus) {

        // retrieve the current block height from the node view
        val currentHeightFromView = Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(retrieveCurrentHeight), timeoutDuration)
          .asInstanceOf[Int]
        log.debug(s"DAG01032022 processNewBlockApplied - current height from view: ${currentHeightFromView}")

        // retrieve genesis block timestamp needed for estimated highest block calculation
        genesisBlockTimestamp = Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView((view: View) =>
          SyncStatusUtil.getGenesisBlockTimestamp(view)), timeoutDuration)
          .asInstanceOf[Long]

        // calculate the estimated highest block given a block correction calculated on how many slots were filled
        val estimatedHighestBlock = Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView((view: View) =>
          SyncStatusUtil.calculateEstimatedHighestBlock(view, timeProvider, params.consensusSecondsInSlot, genesisBlockTimestamp, currentHeightFromView, sidechainBlock.timestamp)), timeoutDuration)
          .asInstanceOf[Int]
        log.debug(s"DAG03032022 processNewBlockApplied - blocks from current to tip (estimated highest block): ${estimatedHighestBlock}")

        // set the current internal state
        syncStatus = true
        currentBlock = currentHeightFromView
        startingBlock = currentHeightFromView
        highestBlock = estimatedHighestBlock
        currentBlockTimestamp = sidechainBlock.timestamp
        schedulerBlockTimestamp = sidechainBlock.timestamp

        // broadcast sync start and its details, starting block is equal to current block in this case
        val syncStatusMessage = new SyncStatus(true, BigInt(currentBlock), BigInt(startingBlock), BigInt(highestBlock))
        context.system.eventStream.publish(NotifySyncStart(syncStatusMessage))

        // start the scheduler, it will compare the block timestamps between two scheduler calls
        checkBlockTimestampsScheduler = context.system.scheduler.scheduleAtFixedRate(
          checkBlocksTimestampInterval, checkBlocksTimestampInterval, self,
          CheckBlockTimestamps)
      }

      // if the node is already syncing we update the current block value adding one for every new block that is applied on the state
      // we also check if the applied block timestamp is near the current timestamp, in this case we set the syncStatus to false and reset the state
      else {

        // update the current block counter and set the current block timestamp
        currentBlock += 1
        currentBlockTimestamp = sidechainBlock.timestamp

        if (checkAppliedBlockTimestamp(sidechainBlock.timestamp)) {
          context.system.eventStream.publish(NotifySyncStop) // broadcast sync stop
          checkBlockTimestampsScheduler.cancel() // stop the scheduler
          resetInternalState() // reset the internal state
        }

        // update the current timestamp every updateHighestBlockCalculationThreshold blocks and reset the counter
        updateHighestBlockCounter += 1
        if (updateHighestBlockCounter.compareTo(updateHighestBlockCalculationThreshold) > 0) {
          val currentHeightFromView = Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(retrieveCurrentHeight), timeoutDuration)
            .asInstanceOf[Int]
          highestBlock = Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView((view: View) =>
            SyncStatusUtil.calculateEstimatedHighestBlock(view, timeProvider, params.consensusSecondsInSlot, genesisBlockTimestamp, currentHeightFromView, sidechainBlock.timestamp)), timeoutDuration)
            .asInstanceOf[Int]
          // reset the update highest block counter
          updateHighestBlockCounter = 0
        }
      }

    }

  }

  // In this method the message BetterNeighbourAppeared is processed
  // set the processNewBlocks flag to true, the actor is able to process new blocks applied to the state
  private def processBetterNeighbour(): Unit = {
    processNewBlocks = true
  }

  protected def processSidechainNodeViewHolderEvents: Receive = {

    case SemanticallySuccessfulModifier(sidechainBlock: PMOD) =>
      processNewBlockApplied(sidechainBlock)

  }

  protected def processSyncTrackerEvents: Receive = {

    case BetterNeighbourAppeared =>
      processBetterNeighbour()

  }

  protected def processSyncStatusScheduler: Receive = {

    case CheckBlockTimestamps =>
      checkBlockTimestamps

  }

  // The processNewBlockApplied in case of syncStatus false and with a new block applied to the state will start a scheduler
  // this scheduler will ping the sync status actor every "checkBlocksTimestampInterval" seconds
  // we will check if the current block timestamp is equal to the previous scheduler call block timestamp (previously saved)
  // if not we will update the scheduler block timestamp (schedulerBlockTimestamp) otherwise we will set the syncStatus false and
  // reset the internal state (this means that the node synchronization is stopped or blocked for some reason)
  protected def checkBlockTimestamps(): Unit = {
    if(schedulerBlockTimestamp.compareTo(currentBlockTimestamp) == 0) {
      context.system.eventStream.publish(NotifySyncStop) // broadcast sync stop
      checkBlockTimestampsScheduler.cancel() // stop the scheduler
      resetInternalState() // reset the internal state
    } else {
      schedulerBlockTimestamp = currentBlockTimestamp
    }
  }

  // used at rpc level (eth_syncing method) to retrieve updated information on the sync status of the node
  protected def returnSyncStatus: Receive = {
    case ReturnSyncStatus =>
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
      processSyncStatusScheduler orElse
      returnSyncStatus orElse {
      case message: Any => log.error("SyncStatusActor received strange message: " + message.getClass.toString)
    }
  }

}

object SyncStatusActor {

  object ReceivableMessages {

    case object ReturnSyncStatus

    case class NotifySyncStart(syncStatus: SyncStatus)

    case object NotifySyncStop

    case object CheckBlockTimestamps

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

