import scorex.core.{ModifierTypeId, NodeViewModifier}
import scorex.core.block.Block
import scorex.core.serialization.Serializer
import scorex.core.settings.ScorexSettings
import scorex.core.utils.NetworkTimeProvider

class SDKNodeViewHolder(sdkSettings: SDKSettings,
                        timeProvider: NetworkTimeProvider)
  extends scorex.core.NodeViewHolder[Transaction, Block[Transaction]] {
  override type SI = scorex.core.consensus.SyncInfo
  override type HIS = SDKHistory
  override type MS = SidechainState
  override type VL = SDKWallet
  override type MP = MemoryPool

  override val scorexSettings: ScorexSettings = sdkSettings.scorexSettings;

  override def restoreState(): Option[(HIS, MS, VL, MP)] = ???

  override protected def genesisState: (HIS, MS, VL, MP) = ???

  // TO DO: put here map of custom sidechain transactions
  val customTransactionSerializers: Map[scorex.core.ModifierTypeId, TransactionSerializer[_ <: Transaction]] = ???

  override val modifierSerializers: Map[ModifierTypeId, Serializer[_ <: NodeViewModifier]] =
    Map(new RegularTransaction().modifierTypeId() -> new SDKTransactionsCompanion(customTransactionSerializers))

}

object SDKNodeViewHolder /*extends ScorexLogging with ScorexEncoding*/ {
  def generateGenesisState(hybridSettings: SDKSettings,
                           timeProvider: NetworkTimeProvider): Unit = ??? // TO DO: change later
}

/*
object SDKNodeViewHolderRef {
  def props(settings: SDKSettings,
            timeProvider: NetworkTimeProvider): Props =
    Props(new SDKNodeViewHolder(settings, timeProvider))

  def apply(settings: SDKSettings,
            timeProvider: NetworkTimeProvider)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(settings, timeProvider))

  def apply(name: String,
            settings: SDKSettings,
            timeProvider: NetworkTimeProvider)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(settings, timeProvider), name)
}
*/