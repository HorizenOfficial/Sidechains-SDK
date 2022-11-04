package com.horizen

import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.chain.AbstractFeePaymentsInfo
import com.horizen.consensus.{FullConsensusEpochInfo, StakeConsensusEpochInfo, blockIdToEpochId}
import com.horizen.node._
import com.horizen.params.NetworkParams
import com.horizen.storage.{AbstractHistoryStorage, SidechainStorageInfo}
import com.horizen.transaction.Transaction
import com.horizen.utils.SDKModifiersCache
import com.horizen.validation._
import sparkz.core.consensus.History.ProgressInfo
import sparkz.core.{ModifiersCache, idToVersion}
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages._
import sparkz.core.utils.NetworkTimeProvider
import sparkz.core.settings.SparkzSettings
import scala.util.{Failure, Success, Try}

abstract class AbstractSidechainNodeViewHolder[
  TX <: Transaction, H <: SidechainBlockHeaderBase, PMOD <: SidechainBlockBase[TX, H]]
(
  sidechainSettings: SidechainSettings,
  timeProvider: NetworkTimeProvider
)
  extends sparkz.core.NodeViewHolder[TX, PMOD]
    with SidechainTypes {
  override type SI = SidechainSyncInfo
  type FPI <: AbstractFeePaymentsInfo
  type HSTOR <: AbstractHistoryStorage[PMOD, FPI, HSTOR]

  override type HIS <: AbstractHistory[TX, H, PMOD, FPI, HSTOR, HIS]
  override type MS <: AbstractState[TX, H, PMOD, MS]
  override type VL <: AbstractWallet[TX, PMOD, VL]

  protected var applyingBlock: Boolean = false

  val maxTxFee: Long = sidechainSettings.wallet.maxTxFee
  val listOfStorageInfo: Seq[SidechainStorageInfo]


  /**
   * Cache for modifiers. If modifiers are coming out-of-order, they are to be stored in this cache.
   */
  protected override lazy val modifiersCache: ModifiersCache[PMOD, HIS] =
    new SDKModifiersCache[PMOD, HIS](sparksSettings.network.maxModifiersCacheSize)


  case class SidechainNodeUpdateInformation(history: HIS,
                                            state: MS,
                                            wallet: VL,
                                            failedMod: Option[PMOD],
                                            alternativeProgressInfo: Option[ProgressInfo[PMOD]],
                                            suffix: IndexedSeq[PMOD])

  override val sparksSettings: SparkzSettings = sidechainSettings.sparkzSettings

  protected def semanticBlockValidators(params: NetworkParams): Seq[SemanticBlockValidator[PMOD]] = Seq(new SidechainBlockSemanticValidator[TX, PMOD](params))

  protected def historyBlockValidators(params: NetworkParams): Seq[HistoryBlockValidator[TX, H, PMOD, FPI, HSTOR, HIS]] = Seq(
    new WithdrawalEpochValidator(params),
    new MainchainPoWValidator(params),
    new MainchainBlockReferenceValidator(params),
    new ConsensusValidator(timeProvider)
  )

  def dumpStorages(): Unit

  def getStorageVersions: Map[String, String]

  override def receive: Receive = {
    applyFunctionOnNodeView orElse
      applyBiFunctionOnNodeView orElse
      getCurrentSidechainNodeViewInfo orElse
      processLocallyGeneratedSecret orElse
      processRemoteModifiers orElse
      applyModifier orElse
      processGetStorageVersions orElse
      processLocallyGeneratedTransaction orElse
      super.receive
  }

  protected def getCurrentSidechainNodeViewInfo: Receive

  protected def applyFunctionOnNodeView: Receive

  protected def applyBiFunctionOnNodeView[T, A]: Receive

  /**
   * Process new modifiers from remote.
   * Put all candidates to modifiersCache and then try to apply as much modifiers from cache as possible.
   * Clear cache if it's size exceeds size limit.
   * Publish `ModifiersProcessingResult` message with all just applied and removed from cache modifiers.
   */
  override def processRemoteModifiers: Receive = {
    case sparkz.core.NodeViewHolder.ReceivableMessages.ModifiersFromRemote(mods: Seq[PMOD]) =>
      mods.foreach(m => modifiersCache.put(m.id, m))

      log.debug(s"Cache size before: ${modifiersCache.size}")

      if (!applyingBlock) {
        applyingBlock = true
        self ! AbstractSidechainNodeViewHolder.InternalReceivableMessages.ApplyModifier(Seq())
      }
  }

  protected def processLocallyGeneratedSecret: Receive = {
    case AbstractSidechainNodeViewHolder.ReceivableMessages.LocallyGeneratedSecret(secret) =>
      vault().addSecret(secret) match {
        case Success(newVault) =>
          updateNodeView(updatedVault = Some(newVault))
          sender() ! Success(Unit)
        case Failure(ex) =>
          sender() ! Failure(ex)
      }
  }

  def applyModifier: Receive = {
    case AbstractSidechainNodeViewHolder.InternalReceivableMessages.ApplyModifier(applied: Seq[PMOD]) => {
      modifiersCache.popCandidate(history()) match {
        case Some(mod) =>
          pmodModify(mod)
          self ! AbstractSidechainNodeViewHolder.InternalReceivableMessages.ApplyModifier(mod +: applied)
        case None => {
          val cleared = modifiersCache.cleanOverfull()
          context.system.eventStream.publish(ModifiersProcessingResult(applied, cleared))
          applyingBlock = false
          log.debug(s"Cache size after: ${modifiersCache.size}")
        }
      }
    }
  }

  def processGetStorageVersions: Receive = {
    case AbstractSidechainNodeViewHolder.ReceivableMessages.GetStorageVersions =>
      sender() ! getStorageVersions
  }

  def processLocallyGeneratedTransaction: Receive


  // This method is actually a copy-paste of parent NodeViewHolder.pmodModify method.
  // The difference is that modifiers are applied to the State and Wallet simultaneously.
  override protected def pmodModify(pmod: PMOD): Unit = {
    if (!history().contains(pmod.id)) {
      context.system.eventStream.publish(StartingPersistentModifierApplication(pmod))

      log.info(s"Apply modifier ${pmod.encodedId} of type ${pmod.modifierTypeId} to nodeViewHolder")

      history().append(pmod) match {
        case Success((historyBeforeStUpdate, progressInfo)) =>
          log.debug(s"Going to apply modifications to the state: $progressInfo")
          context.system.eventStream.publish(SyntacticallySuccessfulModifier(pmod))
          context.system.eventStream.publish(NewOpenSurface(historyBeforeStUpdate.openSurfaceIds()))

          if (progressInfo.toApply.nonEmpty) {
            val (newHistory, newStateTry, newWallet, blocksApplied) =
              updateStateAndWallet(historyBeforeStUpdate, minimalState(), vault(), progressInfo, IndexedSeq())

            newStateTry match {
              case Success(newState) =>
                val newMemPool = updateMemPool(progressInfo.toRemove, blocksApplied, memoryPool(), newState)
                // Note: in parent NodeViewHolder.pmodModify wallet was updated here.

                log.info(s"Persistent modifier ${pmod.encodedId} applied successfully, now updating node view")
                updateNodeView(Some(newHistory), Some(newState), Some(newWallet), Some(newMemPool))

                log.debug(s"Current mempool size: ${newMemPool.size} transactions")
              // TODO FOR MERGE: usedSizeKBytes()/usedPercentage() should be moved into sparkz.core.transaction.MemoryPool
              //                 or a new AbstractMemoryPool class should be created between MP and the concrete classes
              // - ${newMemPool.usedSizeKBytes}kb (${newMemPool.usedPercentage}%)")
              case Failure(e) =>
                log.warn(s"Can`t apply persistent modifier (id: ${pmod.encodedId}, contents: $pmod) to minimal state", e)
                updateNodeView(updatedHistory = Some(newHistory))
                context.system.eventStream.publish(SemanticallyFailedModification(pmod, e))
            }
          } else {
            updateNodeView(updatedHistory = Some(historyBeforeStUpdate))
          }
        case Failure(e) =>
          log.warn(s"Can`t apply persistent modifier (id: ${pmod.encodedId}, contents: $pmod) to history", e)
          context.system.eventStream.publish(SyntacticallyFailedModification(pmod, e))
      }
    } else {
      log.warn(s"Trying to apply modifier ${pmod.encodedId} that's already in history")
    }
  }

  // This method is actually a copy-paste of parent NodeViewHolder.updateState method.
  // The difference is that State is updated together with Wallet.
  def updateStateAndWallet(history: HIS,
                                   state: MS,
                                   wallet: VL,
                                   progressInfo: ProgressInfo[PMOD],
                                   suffixApplied: IndexedSeq[PMOD]): (HIS, Try[MS], VL, Seq[PMOD]) = {

    // Do rollback if chain switch needed
    val (walletToApplyTry: Try[VL], stateToApplyTry: Try[MS], suffixTrimmed: IndexedSeq[PMOD]) = if (progressInfo.chainSwitchingNeeded) {
      @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
      val branchingPoint = progressInfo.branchPoint.get //todo: .get
      if (state.version != branchingPoint) {
        log.debug(s"chain reorg needed, rolling back state and wallet to branching point: ${branchingPoint}")
        (
          wallet.rollback(idToVersion(branchingPoint)),
          state.rollbackTo(idToVersion(branchingPoint)),
          trimChainSuffix(suffixApplied, branchingPoint)
        )
      } else (Success(wallet), Success(state), IndexedSeq())
    } else (Success(wallet), Success(state), suffixApplied)

    (stateToApplyTry, walletToApplyTry) match {
      case (Success(stateToApply), Success(walletToApply)) =>
        log.debug("calling applyStateAndWallet")
        applyStateAndWallet(history, stateToApply, walletToApply, suffixTrimmed, progressInfo) match {
          case Success(nodeUpdateInfo) =>
            nodeUpdateInfo.failedMod match {
              case Some(_) =>
                @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
                val alternativeProgressInfo = nodeUpdateInfo.alternativeProgressInfo.get
                updateStateAndWallet(nodeUpdateInfo.history, nodeUpdateInfo.state, nodeUpdateInfo.wallet, alternativeProgressInfo, nodeUpdateInfo.suffix)
              case None => (nodeUpdateInfo.history, Success(nodeUpdateInfo.state), nodeUpdateInfo.wallet, nodeUpdateInfo.suffix)
            }
          case Failure(ex) =>
            log.warn("applyStateAndWallet returned with a failure", ex)
            (history, Failure(ex), wallet, suffixTrimmed)
        }
      case (Failure(e), _) =>
        log.error("State rollback failed: ", e)
        context.system.eventStream.publish(RollbackFailed)
        //todo: what to return here? the situation is totally wrong
        ???
      case (_, Failure(e)) =>
        log.error("Wallet rollback failed: ", e)
        context.system.eventStream.publish(RollbackFailed)
        //todo: what to return here? the situation is totally wrong
        ???
    }
  }

  // This method is actually a copy-paste of parent NodeViewHolder.trimChainSuffix method.
  protected def trimChainSuffix(suffix: IndexedSeq[PMOD], rollbackPoint: scorex.util.ModifierId): IndexedSeq[PMOD] = {
    val idx = suffix.indexWhere(_.id == rollbackPoint)
    if (idx == -1) IndexedSeq() else suffix.drop(idx)
  }


  // Apply state and wallet with blocks one by one, if consensus epoch is going to be changed -> notify wallet and history.
  protected def applyStateAndWallet(history: HIS,
                                    stateToApply: MS,
                                    walletToApply: VL,
                                    suffixTrimmed: IndexedSeq[PMOD],
                                    progressInfo: ProgressInfo[PMOD]): Try[SidechainNodeUpdateInformation] = {

    val updateInfoSample = SidechainNodeUpdateInformation(history, stateToApply, walletToApply, None, None, suffixTrimmed)

    progressInfo.toApply.foldLeft[Try[SidechainNodeUpdateInformation]](Success(updateInfoSample)) {
      case (f@Failure(ex), _) =>
        log.error("Reporting modifier failed", ex)
        f
      case (success@Success(updateInfo), modToApply) =>
        if (updateInfo.failedMod.isEmpty) {

          // Check if the next modifier will change Consensus Epoch, so notify History and Wallet with current info.
          val (newHistory, newWallet): (HIS, VL) = Try {
            if (updateInfo.state.isSwitchingConsensusEpoch(modToApply.timestamp)) {
              log.debug("Switching consensus epoch")

              val (lastBlockInEpoch, consensusEpochInfo) = updateInfo.state.getCurrentConsensusEpochInfo
              val nonceConsensusEpochInfo = updateInfo.history.calculateNonceForEpoch(blockIdToEpochId(lastBlockInEpoch))
              val stakeConsensusEpochInfo = StakeConsensusEpochInfo(consensusEpochInfo.forgingStakeInfoTree.rootHash(), consensusEpochInfo.forgersStake)

              val historyAfterConsensusInfoApply =
                updateInfo.history.applyFullConsensusInfo(lastBlockInEpoch, FullConsensusEpochInfo(stakeConsensusEpochInfo, nonceConsensusEpochInfo))

              val walletAfterStakeConsensusApply = updateInfo.wallet.applyConsensusEpochInfo(consensusEpochInfo)
              (historyAfterConsensusInfoApply, walletAfterStakeConsensusApply)
            } else
              (updateInfo.history, updateInfo.wallet)
          } match {
            // any exception thrown above will make this method return with a success try-obj which contains the information of the failure, similarly
            // to what we do below when applying modifier to the state
            case Failure(ex) =>
              log.error(s"Could not apply modifier ${modToApply.id}, exception:", ex)
              val failureInformation : Try[SidechainNodeUpdateInformation] = updateInfo.history.reportModifierIsInvalid(modToApply, progressInfo).map {
                case (historyAfterApply, newProgressInfo) =>
                  context.system.eventStream.publish(SemanticallyFailedModification(modToApply, ex))
                  SidechainNodeUpdateInformation(historyAfterApply, updateInfo.state, updateInfo.wallet, Some(modToApply), Some(newProgressInfo), updateInfo.suffix)
              }
              return failureInformation
            case Success(success) =>
              success
          }

          // if a crash happens here the inconsistency between state and history wont appear: we should check the wallet storages and if a inconsistency is seen, rollback it
          // we have:
          //   1. state == history
          //   2. (wallet storages set) != state because of forgerBoxStorage
          //   3. history consensus storage has evolved as well but it has no rollback points

          //   At the restart all the update above would be re-applied, but in the meanwhile (before re-update it) such data might be used
          //   for instance in the forging phase or even in the validation phase.
          //   To rule out this possibility, even in case of future modifications,
          //   we can find a common root between state and ForgerBoxStorage versions and roll back up to that point

          updateInfo.state.applyModifier(modToApply) match {
            case Success(stateAfterApply) =>
              log.debug("success: modifier applied to state, blockInfo: " + newHistory.blockInfoById(modToApply.id))

              context.system.eventStream.publish(SemanticallySuccessfulModifier(modToApply))

              val stateWithdrawalEpochNumber: Int = stateAfterApply.getWithdrawalEpochInfo.epoch
              val (historyResult, walletResult) = if (stateAfterApply.isWithdrawalEpochLastIndex) {
                val feePayments = getFeePaymentsInfo(stateAfterApply, stateWithdrawalEpochNumber)
                var historyAfterUpdateFee = newHistory
                if (!feePayments.isEmpty) {
                  historyAfterUpdateFee = newHistory.updateFeePaymentsInfo(modToApply.id, feePayments)
                }

                val walletAfterApply = getScanPersistentWallet(modToApply, Some(stateAfterApply), stateWithdrawalEpochNumber, newWallet)
                (historyAfterUpdateFee, walletAfterApply)
              } else {
                val walletAfterApply = getScanPersistentWallet(modToApply, None, stateWithdrawalEpochNumber, newWallet)
                (newHistory, walletAfterApply)
              }

              // as a final step update the history (validity and best block info), in this way we can check
              // at the startup the consistency of state and history storage versions and be sure that also intermediate steps
              // are consistent
              historyResult.reportModifierIsValid(modToApply).map { newHistory =>
                log.debug("success: modifier applied to history, blockInfo " + newHistory.blockInfoById(modToApply.id))

                SidechainNodeUpdateInformation(newHistory, stateAfterApply, walletResult, None, None, updateInfo.suffix :+ modToApply)
              }

            case Failure(e) =>
              log.error(s"Could not apply modifier ${modToApply.id}, exception:" + e)
              newHistory.reportModifierIsInvalid(modToApply, progressInfo).map {
                case (historyAfterApply, newProgressInfo) =>
                  context.system.eventStream.publish(SemanticallyFailedModification(modToApply, e))
                  SidechainNodeUpdateInformation(historyAfterApply, updateInfo.state, newWallet, Some(modToApply), Some(newProgressInfo), updateInfo.suffix)
              }
          }
        } else success
    } recoverWith {
      case exception =>
        log.error(s"Unexpected error while processing info: ${progressInfo.toString()}", exception)
        Failure(exception)
    }
  }



  // Check if the next modifier will change Consensus Epoch, so notify History and Wallet with current info.
  protected def applyConsensusEpochInfo(history: HIS, state: MS, wallet: VL, modToApply: PMOD): (HIS, VL)

  def getFeePaymentsInfo(state: MS, epochNumber: Int) : FPI
  def getScanPersistentWallet(modToApply: PMOD, stateOp: Option[MS], epochNumber: Int, wallet: VL) : VL

  // Check is the modifier ends the withdrawal epoch, so notify History and Wallet about fees to be payed.
  // Scan modifier by the Wallet considering the forger fee payments.
  protected def scanBlockWithFeePayments(history: HIS, state: MS, wallet: VL, modToApply: PMOD): (HIS, VL) = {
    val stateWithdrawalEpochNumber: Int = state.getWithdrawalEpochInfo.epoch
    if (state.isWithdrawalEpochLastIndex) {
      val historyAfterUpdateFee = history.updateFeePaymentsInfo(modToApply.id, getFeePaymentsInfo(state, stateWithdrawalEpochNumber))
      val walletAfterApply: VL = getScanPersistentWallet(modToApply, Some(state), stateWithdrawalEpochNumber, wallet)
      (historyAfterUpdateFee, walletAfterApply)
    } else {
      val walletAfterApply: VL = getScanPersistentWallet(modToApply, None, stateWithdrawalEpochNumber, wallet)
      (history, walletAfterApply)
    }
  }



}

object AbstractSidechainNodeViewHolder {
  object ReceivableMessages {
    case class GetDataFromCurrentSidechainNodeView[TX <: Transaction,
      H <: SidechainBlockHeaderBase,
      PMOD <: SidechainBlockBase[TX, H],
      FPI <: AbstractFeePaymentsInfo,
      NH <: NodeHistoryBase[TX, H, PMOD, FPI],
      NS <: NodeStateBase,
      NW <: NodeWalletBase,
      NP <: NodeMemoryPoolBase[TX],
      NV <: SidechainNodeViewBase[TX, H, PMOD, FPI, NH, NS, NW, NP],
      A](f: NV => A)

    case class LocallyGeneratedSecret[S <: SidechainTypes#SCS](secret: S)

    case class ApplyFunctionOnNodeView[TX <: Transaction,
      H <: SidechainBlockHeaderBase,
      PMOD <: SidechainBlockBase[TX, H],
      FPI <: AbstractFeePaymentsInfo,
      NH <: NodeHistoryBase[TX, H, PMOD, FPI],
      NS <: NodeStateBase,
      NW <: NodeWalletBase,
      NP <: NodeMemoryPoolBase[TX],
      NV <: SidechainNodeViewBase[TX, H, PMOD, FPI, NH, NS, NW, NP],
      A](f: java.util.function.Function[NV, A])

    case class ApplyBiFunctionOnNodeView[
      TX <: Transaction,
      H <: SidechainBlockHeaderBase,
      PMOD <: SidechainBlockBase[TX, H],
      FPI <: AbstractFeePaymentsInfo,
      NH <: NodeHistoryBase[TX, H, PMOD, FPI],
      NS <: NodeStateBase,
      NW <: NodeWalletBase,
      NP <: NodeMemoryPoolBase[TX],
      NV <: SidechainNodeViewBase[TX, H, PMOD, FPI, NH, NS, NW, NP],
      T,
      A](f: java.util.function.BiFunction[NV, T, A], functionParameter: T)

    case object GetStorageVersions

  }

  protected[horizen] object InternalReceivableMessages {
    case class ApplyModifier[PMOD](applied: Seq[PMOD])

    sealed trait NewLocallyGeneratedTransactions[TX <: Transaction] {
      val txs: Iterable[TX]
    }

    case class LocallyGeneratedTransaction[TX <: Transaction](tx: TX) extends NewLocallyGeneratedTransactions[TX] {
      override val txs: Iterable[TX] = Iterable(tx)
    }
  }

}

