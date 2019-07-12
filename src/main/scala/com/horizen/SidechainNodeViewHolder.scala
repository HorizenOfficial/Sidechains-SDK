package com.horizen

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen.block.SidechainBlock
import com.horizen.box.BoxSerializer
import com.horizen.companion.{SidechainBoxesCompanion, SidechainSecretsCompanion, SidechainTransactionsCompanion}
import com.horizen.secret.SecretSerializer
import com.horizen.state.{ApplicationState, DefaultApplicationState}
import com.horizen.wallet.{ApplicationWallet, DefaultApplicationWallet}
import scorex.core.settings.ScorexSettings
import scorex.core.utils.NetworkTimeProvider
import scorex.util.ScorexLogging

import scala.util.{Failure, Success, Try}

class SidechainNodeViewHolder(sidechainSettings: SidechainSettings,
                              timeProvider: NetworkTimeProvider)
  extends scorex.core.NodeViewHolder[SidechainTypes#SCBT, SidechainBlock]
  with ScorexLogging
  with SidechainTypes
{
  override type SI = scorex.core.consensus.SyncInfo
  override type HIS = SidechainHistory
  override type MS = SidechainState
  override type VL = SidechainWallet
  override type MP = SidechainMemoryPool

  override val scorexSettings: ScorexSettings = sidechainSettings.scorexSettings;

  override def restoreState(): Option[(HIS, MS, VL, MP)] = {
    SidechainNodeViewHolder.restoreState(sidechainSettings, timeProvider)
  }

  override protected def genesisState: (HIS, MS, VL, MP) = {
    SidechainNodeViewHolder.generateGenesisState(sidechainSettings, timeProvider)
  }

  // TO DO: Put it into NodeViewSynchronizerRef::modifierSerializers. Also put here map of custom sidechain transactions
  /*val customTransactionSerializers: Map[scorex.core.ModifierTypeId, TransactionSerializer[_ <: Transaction]] = ???

  override val modifierSerializers: Map[Byte, Serializer[_ <: NodeViewModifier]] =
    Map(new RegularTransaction().modifierTypeId() -> new SidechainTransactionsCompanion(customTransactionSerializers))
  */
}

object SidechainNodeViewHolder
{

  val defaultBoxesCompanion : SidechainBoxesCompanion = SidechainBoxesCompanion(new JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]]())
  val defaultSecretCompanion : SidechainSecretsCompanion = SidechainSecretsCompanion(new JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]]())

  val defaultApplicationWallet: ApplicationWallet = new DefaultApplicationWallet()
  val defaultApplicationState: ApplicationState = new DefaultApplicationState()

  def geneisBlocks : Seq[SidechainBlock] = Seq()

  def restoreState(sidechainSettings: SidechainSettings,
                   timeProvider: NetworkTimeProvider) :
    Option[(SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool)] = {
    Try {
      val history = new SidechainHistory()

      val wallet = SidechainWallet.restoreWallet(sidechainSettings, defaultBoxesCompanion, defaultSecretCompanion, defaultApplicationWallet) match {
        case Success(w) => w
        case Failure(exception) => throw exception
      }

      val state = SidechainState.restoreState(sidechainSettings, defaultBoxesCompanion, defaultApplicationState) match {
        case Success(s) => s
        case Failure(exception) => throw exception
      }

      val pool = SidechainMemoryPool.emptyPool

      (history, state, wallet, pool)
    }.toOption
  }

  def generateGenesisState(sidechainSettings: SidechainSettings,
                           timeProvider: NetworkTimeProvider):
    (SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool) = {

    (new SidechainHistory(),
      SidechainState.genesisState(sidechainSettings, defaultBoxesCompanion, defaultApplicationState, geneisBlocks).get,
      SidechainWallet.genesisWallet(sidechainSettings, defaultBoxesCompanion, defaultSecretCompanion, defaultApplicationWallet, geneisBlocks).get,
      SidechainMemoryPool.emptyPool
    )
  }
}

object SidechainNodeViewHolderRef {
  def props(settings: SidechainSettings,
            timeProvider: NetworkTimeProvider): Props =
    Props(new SidechainNodeViewHolder(settings, timeProvider))

  def apply(settings: SidechainSettings,
            timeProvider: NetworkTimeProvider)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(settings, timeProvider))

  def apply(name: String,
            settings: SidechainSettings,
            timeProvider: NetworkTimeProvider)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(settings, timeProvider), name)
}
