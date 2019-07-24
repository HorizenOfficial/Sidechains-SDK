package com.horizen


import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen.block.SidechainBlock
import com.horizen.companion.{SidechainBoxesCompanion, SidechainSecretsCompanion, SidechainTransactionsCompanion}
import com.horizen.params.NetworkParams
import com.horizen.state.ApplicationState
import com.horizen.wallet.ApplicationWallet
import scorex.core.settings.ScorexSettings
import scorex.core.utils.NetworkTimeProvider
import scorex.util.ScorexLogging


class SidechainNodeViewHolder(sidechainSettings: SidechainSettings,
                              params: NetworkParams,
                              timeProvider: NetworkTimeProvider,
                              sidechainBoxesCompanion: SidechainBoxesCompanion,
                              sidechainSecretsCompanion: SidechainSecretsCompanion,
                              sidechainTransactionsCompanion: SidechainTransactionsCompanion,
                              applicationWallet: ApplicationWallet,
                              applicationState: ApplicationState)
  extends scorex.core.NodeViewHolder[SidechainTypes#SCBT, SidechainBlock]
  with ScorexLogging
  with SidechainTypes
{
  override type SI = SidechainSyncInfo
  override type HIS = SidechainHistory
  override type MS = SidechainState
  override type VL = SidechainWallet
  override type MP = SidechainMemoryPool

  override val scorexSettings: ScorexSettings = sidechainSettings.scorexSettings

  override def restoreState(): Option[(HIS, MS, VL, MP)] = {
    val history = SidechainHistory.restoreHistory(sidechainSettings, sidechainTransactionsCompanion, params, None)
    val wallet = SidechainWallet.restoreWallet(sidechainSettings, applicationWallet, sidechainBoxesCompanion, sidechainSecretsCompanion, None)
    val state = SidechainState.restoreState(sidechainSettings, applicationState, sidechainBoxesCompanion, None)
    val pool = SidechainMemoryPool.emptyPool

    if (history.isDefined && wallet.isDefined && state.isDefined)
      Some((history.get, state.get, wallet.get, pool))
    else
      None
  }

  override protected def genesisState: (HIS, MS, VL, MP) = {
    try {
      val history = SidechainHistory.genesisHistory(sidechainSettings, sidechainTransactionsCompanion, params, None)
      val state = SidechainState.genesisState(sidechainSettings, applicationState, sidechainBoxesCompanion, None)
      val wallet = SidechainWallet.genesisWallet(sidechainSettings, applicationWallet, sidechainBoxesCompanion, sidechainSecretsCompanion, None)
      val pool = SidechainMemoryPool.emptyPool

      if (history.isDefined && wallet.isDefined && state.isDefined)
        (history.get, state.get, wallet.get, pool)
      else {
        if (history.isEmpty)
          throw new RuntimeException("History storage is not empty.")

        if (state.isEmpty)
          throw new RuntimeException("State storage is not empty.")

        if (wallet.isEmpty)
          throw new RuntimeException("WalletBox storage is not empty.")

        (null, null, null, null)
      }

    } catch {
      case exception : Throwable =>
        log.error ("Error during creation genesis state.", exception)
        throw exception
    }
  }

  // TO DO: Put it into NodeViewSynchronizerRef::modifierSerializers. Also put here map of custom sidechain transactions
  /*val customTransactionSerializers: Map[scorex.core.ModifierTypeId, TransactionSerializer[_ <: Transaction]] = ???

  override val modifierSerializers: Map[Byte, Serializer[_ <: NodeViewModifier]] =
    Map(new RegularTransaction().modifierTypeId() -> new SidechainTransactionsCompanion(customTransactionSerializers))
  */
}

object SidechainNodeViewHolderRef {
  def props(settings: SidechainSettings,
            params: NetworkParams,
            timeProvider: NetworkTimeProvider,
            sidechainBoxesCompanion: SidechainBoxesCompanion,
            sidechainSecretsCompanion: SidechainSecretsCompanion,
            sidechainTransactionsCompanion: SidechainTransactionsCompanion,
            applicationWallet: ApplicationWallet,
            applicationState: ApplicationState): Props =
    Props(new SidechainNodeViewHolder(settings, params, timeProvider, sidechainBoxesCompanion, sidechainSecretsCompanion,
      sidechainTransactionsCompanion, applicationWallet, applicationState))

  def apply(settings: SidechainSettings,
            params: NetworkParams,
            timeProvider: NetworkTimeProvider,
            sidechainBoxesCompanion: SidechainBoxesCompanion,
            sidechainSecretsCompanion: SidechainSecretsCompanion,
            sidechainTransactionsCompanion: SidechainTransactionsCompanion,
            applicationWallet: ApplicationWallet,
            applicationState: ApplicationState)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(settings, params, timeProvider, sidechainBoxesCompanion, sidechainSecretsCompanion,
      sidechainTransactionsCompanion, applicationWallet, applicationState))

  def apply(name: String,
            settings: SidechainSettings,
            params: NetworkParams,
            timeProvider: NetworkTimeProvider,
            sidechainBoxesCompanion: SidechainBoxesCompanion,
            sidechainSecretsCompanion: SidechainSecretsCompanion,
            sidechainTransactionsCompanion: SidechainTransactionsCompanion,
            applicationWallet: ApplicationWallet,
            applicationState: ApplicationState)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(settings, params, timeProvider, sidechainBoxesCompanion, sidechainSecretsCompanion,
      sidechainTransactionsCompanion, applicationWallet, applicationState), name)
}
