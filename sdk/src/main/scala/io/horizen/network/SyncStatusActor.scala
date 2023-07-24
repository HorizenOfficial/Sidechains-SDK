package io.horizen.network

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
import akka.pattern.ask
import akka.util.Timeout
import io.horizen._
import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.chain.AbstractFeePaymentsInfo
import io.horizen.consensus.ConsensusParamsUtil
import io.horizen.history.AbstractHistory
import io.horizen.network.SyncStatusActor.InternalReceivableMessages.CheckBlocksDensity
import io.horizen.network.SyncStatusActor.ReceivableMessages.GetSyncStatus
import io.horizen.network.SyncStatusActor.{CLOSE_ENOUGH_SLOTS_TO_IGNORE, HIGHEST_BLOCK_CHECK_FREQUENCY, NotifySyncStart, NotifySyncStop, NotifySyncUpdate, SYNC_UPDATE_EVENT_FREQUENCY}
import io.horizen.params.NetworkParams
import io.horizen.storage.AbstractHistoryStorage
import io.horizen.transaction.Transaction
import io.horizen.wallet.Wallet
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.SemanticallySuccessfulModifier
import sparkz.core.transaction.MemoryPool
import sparkz.core.utils.NetworkTimeProvider
import sparkz.util.{ModifierId, SparkzLogging}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.{DurationInt, FiniteDuration, pairIntToDuration}
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class SyncStatusActor[
  TX <: Transaction, H <: SidechainBlockHeaderBase,
  PMOD <: SidechainBlockBase[TX, H],
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
  lazy val timeoutDuration: FiniteDuration = settings.sparkzSettings.restApi.timeout
  implicit lazy val timeout: Timeout = Timeout(timeoutDuration)

  private var isSyncing: Boolean = false
  private var isSyncStartEventSent: Boolean = false
  private var startingBlock: Int = 0
  private var highestBlock: Int = 0

  // Start the scheduler, it will compare the new block events density between two scheduler calls
  private val checkBlocksDensityInterval: FiniteDuration = 15 seconds
  private val checkBlocksDensityScheduler: Cancellable = context.system.scheduler.scheduleAtFixedRate(
      checkBlocksDensityInterval, checkBlocksDensityInterval, self, CheckBlocksDensity)

  private var appliedBlocksNumber: Int = 0
  private var prevAppliedBlocksNumber: Int = 0

  private var currentBlock: Int = -1
  private val maxLastBlockIds: Int = 101 // longest fork we may have + 1
  private val lastAppliedBlockIds: ListBuffer[ModifierId] = ListBuffer.empty // from newest to oldest

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[SemanticallySuccessfulModifier[PMOD]])
  }

  override def postStop(): Unit = {
    log.debug("SyncStatusActor is stopping...")
    super.postStop()
  }

  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
    log.error("SyncStatusActor was restarted because of: ", reason)
    // Subscribe to events after actor restart
    context.system.eventStream.subscribe(self, classOf[SemanticallySuccessfulModifier[PMOD]])
  }

  // The expected max new tips events when the node is already synced and receives new tips from the network
  private def getStandardBlockRate(): Int = {
    Math.ceil(checkBlocksDensityInterval.toSeconds.toDouble / ConsensusParamsUtil.getConsensusSecondsInSlotsPerEpoch(Option.empty)).toInt + 1
  }

  // Returns true if the block timestamp is close enough to the current time.
  // Returns false otherwise.
  private def isCloseEnough(blockTimestamp: Long): Boolean = {
    ((timeProvider.time() / 1000) - blockTimestamp) < (ConsensusParamsUtil.getConsensusSecondsInSlotsPerEpoch(Option.empty) * CLOSE_ENOUGH_SLOTS_TO_IGNORE)
  }

  private def stopSyncing(): Unit = {
    if (isSyncing && isSyncStartEventSent) {
      // It may happen that Sync was detected just before getting close enough to the current time.
      // Before the NotifySyncStart event
      log.debug(s"SyncStatusActor ${settings.sparkzSettings.network.nodeName} " +
        s"sync STOP event published starting = $startingBlock, current = $currentBlock, highest = $highestBlock")
      context.system.eventStream.publish(NotifySyncStop)
    }

    isSyncing = false
    isSyncStartEventSent = false
    startingBlock = 0
    highestBlock = 0
    appliedBlocksNumber = 0
    prevAppliedBlocksNumber = 0
  }

  // Note: this method doesn't separate locally and remote generated blocks
  protected def processNewBlockApplied(sidechainBlock: PMOD): Unit = {
    // For the first block received, retrieve the current tip height known to the SyncStatusActor
    if (currentBlock == -1) {
      Try {
        Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView((view: View) => {
          view.history.blockInfoById(sidechainBlock.id).height
        }), timeoutDuration).asInstanceOf[Int]
      } match {
        case Success(height) => currentBlock = height
        case Failure(ex) => log.warn(s"SyncStatusActor exception occurred during current block height processing: $ex")
      }
    }

    // Update current height and last applied block ids
    lastAppliedBlockIds.headOption.foreach {
      parentId: ModifierId => {
        // Check if the current block is a continuation of the tip
        if (sidechainBlock.parentId == parentId) {
          currentBlock += 1
        } else {
          // Fork branch applied
          val revertedBlocks: Int = lastAppliedBlockIds.indexOf(sidechainBlock.parentId)

          if (revertedBlocks == -1) {
            // Crash the actor throwing an exception and start from scratch
            val stoppingMessage = s"SyncStatusActor: unexpected new tip ${sidechainBlock.id} appeared"
            val noMatchingBlocksException = new IllegalStateException(stoppingMessage)
            // We can encounter two cases:
            // - If the internal applied block IDs list has less then 100 elements we thrown an exception without stack trace
            //   and log the actor restart at Warn level. This can happen in this case:
            //   forger node recently restarted that has created some blocks on its own and then receive valid blocks from
            //   the peers that are applied due to the longest chain rule
            if (lastAppliedBlockIds.length < 100) {
              log.warn(stoppingMessage + " due to recent node restart")
              noMatchingBlocksException.setStackTrace(Array.empty)  // remove the stack trace
              throw noMatchingBlocksException
            }
            // - Otherwise log the entire stack trace and and log the actor at Error level
            else {
              log.error(stoppingMessage); throw noMatchingBlocksException
            }
          }

          currentBlock = currentBlock - revertedBlocks + 1
          lastAppliedBlockIds.drop(revertedBlocks)
          // We must not consider fork blocks of the same height as "syncing"
          // Note: appliedBlockNumber can reach a negative value
          appliedBlocksNumber -= revertedBlocks
        }
      }
    }
    appliedBlocksNumber += 1
    lastAppliedBlockIds.prepend(sidechainBlock.id)
    if (lastAppliedBlockIds.size > maxLastBlockIds)
      lastAppliedBlockIds.dropRight(1)

    // Check if the applied blocks density since the scheduler last check is big enough to consider ourselves syncing
    val appliedBlocksNumberSinceSchedulerLastCheck = appliedBlocksNumber - prevAppliedBlocksNumber
    if(appliedBlocksNumberSinceSchedulerLastCheck > getStandardBlockRate)
      isSyncing = true

    isSyncing match {
      case false => // do nothing
      case true if isCloseEnough(sidechainBlock.timestamp) =>
        stopSyncing()
      case true =>
        if (!isSyncStartEventSent || appliedBlocksNumber % HIGHEST_BLOCK_CHECK_FREQUENCY == 0 || currentBlock == highestBlock) {
          // Calculate the estimated highest block given a block correction calculated on how many slots were filled
          // Try when:
          // 1. it has just been detected that we are syncing;
          // 2. every updateHighestBlockFrequency blocks;
          // 3. previous attempt was underestimated and new tip reached the estimation height.
          Try {
            Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView((view: View) => {
              SyncStatusUtil.calculateEstimatedHighestBlock(view, timeProvider, ConsensusParamsUtil.getConsensusSecondsInSlotsPerEpoch(Option.empty),
                params.sidechainGenesisBlockTimestamp, currentBlock, sidechainBlock.timestamp)
            }), timeoutDuration).asInstanceOf[Int]
          } match {
            case Success(estimatedHighestBlock) => highestBlock = estimatedHighestBlock
            case Failure(ex) => log.warn(s"SyncStatusActor exception occurred during estimated highest block processing: $ex")
          }
        }

        if (!isSyncStartEventSent) {
          // Syncing status is detected with some delay -> consider it for the starting block
          startingBlock = currentBlock - appliedBlocksNumberSinceSchedulerLastCheck + 1

          log.debug(s"SyncStatusActor ${settings.sparkzSettings.network.nodeName} " +
            s"sync START event published starting = $startingBlock, current = $currentBlock, highest = $highestBlock")
          val syncStatusMessage = new SyncStatus(true, BigInt(currentBlock), BigInt(startingBlock), BigInt(highestBlock))
          context.system.eventStream.publish(NotifySyncStart(syncStatusMessage))
          isSyncStartEventSent = true
        } else if((currentBlock - startingBlock) % SYNC_UPDATE_EVENT_FREQUENCY == 0) {
          // Every N new blocks emit SyncUpdate event
          log.debug(s"SyncStatusActor ${settings.sparkzSettings.network.nodeName} " +
            s"sync UPDATE event published starting = $startingBlock, current = $currentBlock, highest = $highestBlock")
          val syncStatusMessage = new SyncStatus(true, BigInt(currentBlock), BigInt(startingBlock), BigInt(highestBlock))
          context.system.eventStream.publish(NotifySyncUpdate(syncStatusMessage))
        }
    }
  }

  protected def processSidechainNodeViewHolderEvents: Receive = {
    case SemanticallySuccessfulModifier(sidechainBlock: PMOD) =>
      processNewBlockApplied(sidechainBlock)
  }

  protected def processSyncStatusScheduler: Receive = {
    case CheckBlocksDensity => // TODO: better to set bigger priority for CheckBlocksDensity
      // Update the counters
      val appliedBlocksNumberBetweenChecks: Int = appliedBlocksNumber - prevAppliedBlocksNumber
      prevAppliedBlocksNumber = appliedBlocksNumber

      if(appliedBlocksNumberBetweenChecks <= getStandardBlockRate && isSyncing) {
        // We have considered ourselves as "syncing" before,
        // but from the last scheduler event haven't received enough new tips
        stopSyncing()
      }
  }
  protected def returnSyncStatus: Receive = {
    case GetSyncStatus =>
      if (isSyncStartEventSent) {
        sender() ! new SyncStatus(isSyncStartEventSent, currentBlock, startingBlock, highestBlock)
      } else {
        sender() ! new SyncStatus(isSyncStartEventSent)
      }
  }

  override def receive: Receive = {
    processSidechainNodeViewHolderEvents orElse
      processSyncStatusScheduler orElse
      returnSyncStatus orElse {
      case message: Any => log.error("SyncStatusActor received strange message: " + message.getClass.toString)
    }
  }

}

object SyncStatusActor {
  val CLOSE_ENOUGH_SLOTS_TO_IGNORE: Int = 2
  val HIGHEST_BLOCK_CHECK_FREQUENCY: Int = 20000
  val SYNC_UPDATE_EVENT_FREQUENCY: Int = 500

  sealed trait SyncEvent

  case class NotifySyncStart(syncStatus: SyncStatus) extends SyncEvent
  case class NotifySyncUpdate(syncStatus: SyncStatus) extends SyncEvent
  case object NotifySyncStop extends SyncEvent

  object ReceivableMessages {
    case object GetSyncStatus
  }

  private[network] object InternalReceivableMessages {
    case object CheckBlocksDensity
  }
}

object SyncStatusActorRef {
  def props[
    TX <: Transaction, H <: SidechainBlockHeaderBase,
    PMOD <: SidechainBlockBase[TX, H],
    FPI <: AbstractFeePaymentsInfo,
    HSTOR <: AbstractHistoryStorage[PMOD, FPI, HSTOR],
    HIS <: AbstractHistory[TX, H, PMOD, FPI, HSTOR, HIS],
    MS <: AbstractState[TX, H, PMOD, MS],
    VL <: Wallet[SidechainTypes#SCS, SidechainTypes#SCP, TX, PMOD, VL],
    MP <: MemoryPool[TX, MP]
  ](settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams, timeProvider: NetworkTimeProvider)
   (implicit ec: ExecutionContext): Props =
    Props(new SyncStatusActor[TX, H, PMOD, FPI, HSTOR, HIS, MS, VL, MP](settings, sidechainNodeViewHolderRef, params, timeProvider))

  def apply[
    TX <: Transaction, H <: SidechainBlockHeaderBase,
    PMOD <: SidechainBlockBase[TX, H],
    FPI <: AbstractFeePaymentsInfo,
    HSTOR <: AbstractHistoryStorage[PMOD, FPI, HSTOR],
    HIS <: AbstractHistory[TX, H, PMOD, FPI, HSTOR, HIS],
    MS <: AbstractState[TX, H, PMOD, MS],
    VL <: Wallet[SidechainTypes#SCS, SidechainTypes#SCP, TX, PMOD, VL],
    MP <: MemoryPool[TX, MP]
  ](name: String, settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams, timeProvider: NetworkTimeProvider)
   (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props[TX, H, PMOD, FPI, HSTOR, HIS, MS, VL, MP](settings, sidechainNodeViewHolderRef, params, timeProvider), name)

  def apply[
    TX <: Transaction, H <: SidechainBlockHeaderBase,
    PMOD <: SidechainBlockBase[TX, H],
    FPI <: AbstractFeePaymentsInfo,
    HSTOR <: AbstractHistoryStorage[PMOD, FPI, HSTOR],
    HIS <: AbstractHistory[TX, H, PMOD, FPI, HSTOR, HIS],
    MS <: AbstractState[TX, H, PMOD, MS],
    VL <: Wallet[SidechainTypes#SCS, SidechainTypes#SCP, TX, PMOD, VL],
    MP <: MemoryPool[TX, MP]
  ](settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams, timeProvider: NetworkTimeProvider)
   (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props[TX, H, PMOD, FPI, HSTOR, HIS, MS, VL, MP](settings, sidechainNodeViewHolderRef, params, timeProvider))
}
