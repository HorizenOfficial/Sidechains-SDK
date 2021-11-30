package com.horizen.csw

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.csw.CswManager.{ProofInProcess, ProofInQueue}
import com.horizen.csw.CswManager.ReceivableMessages.{GenerateCswProof, GetBoxNullifier, GetCeasedStatus, GetCswBoxIds, GetCswInfo}
import com.horizen.csw.CswManager.Responses.{Absent, CswInfo, CswProofInfo, Generated, InProcess, InQueue, InvalidAddress, NoProofData, ProofCreationFinished, ProofGenerationInProcess, ProofGenerationStarted, SidechainIsAlive}
import com.horizen.{SidechainAppEvents, SidechainHistory, SidechainMemoryPool, SidechainSettings, SidechainState, SidechainWallet}
import com.horizen.params.NetworkParams
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, CswData, ForwardTransferCswData, UtxoCswData}
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.ChangedState
import scorex.util.ScorexLogging

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class CswManager(settings: SidechainSettings,
                 params: NetworkParams,
                 sidechainNodeViewHolderRef: ActorRef) (implicit ec: ExecutionContext)
  extends Actor with ScorexLogging {

  import com.horizen.csw.CswManager.InternalReceivableMessages.{CswProofFailed, CswProofSuccessfullyGenerated, TryToScheduleProofGeneration}
  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]

  val timeoutDuration: FiniteDuration = settings.scorexSettings.restApi.timeout
  implicit val timeout: Timeout = Timeout(timeoutDuration)

  var hasSidechainCeased: Boolean = false
  var cswWitnessHolderOpt: Option[CswWitnessHolder] = None

  val proofsInQueue: mutable.ListBuffer[ProofInQueue] = mutable.ListBuffer()
  var proofInProcessOpt: Option[ProofInProcess] = None
  val generatedProofsMap: mutable.Map[ByteArrayWrapper, CswProofInfo] = mutable.Map()

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, SidechainAppEvents.SidechainApplicationStart.getClass)
    context.system.eventStream.subscribe(self, classOf[ChangedState[SidechainState]])
    context.become(initialization)
  }

  override def receive: Receive = {
    reportStrangeInput
  }

  private def initialization: Receive = {
    onApplicationStart orElse
      reportStrangeInput
  }

  private[csw] def workingCycle: Receive = {
    onChangedState orElse
      onGetCeasedStatus orElse
      onGetBoxNullifier orElse
      onGetCswBoxIds orElse
      onGenerateCswProof orElse
      onGetCswInfo orElse
      tryScheduleProofGeneration orElse
      processProofGenerationResults orElse
      reportStrangeInput
  }

  private def reportStrangeInput: Receive = {
    case nonsense =>
      log.warn(s"Strange input for CswManager: $nonsense")
  }

  private def onApplicationStart: Receive = {
    case SidechainAppEvents.SidechainApplicationStart =>
      def getCeasedStatus(sidechainNodeView: View): Boolean = { sidechainNodeView.state.hasCeased }

      val checkAsFuture = (sidechainNodeViewHolderRef ? GetDataFromCurrentView(getCeasedStatus)).asInstanceOf[Future[Boolean]]
      checkAsFuture.onComplete {
        case Success(hasCeased) =>
          hasSidechainCeased = hasCeased
          if(hasCeased)
            loadCswWitness()
          context.become(workingCycle)

        case Failure(ex) =>
          log.error("CswManager: Failed to check sidechain status during node initialization: " + ex)
          context.stop(self)
      }
  }

  private def onChangedState: Receive = {
    case ChangedState(state: SidechainState) =>
      val hasCeased = state.hasCeased
      if(hasSidechainCeased != hasCeased) {
        hasSidechainCeased = hasCeased
        if(hasSidechainCeased) {
          log.info("CswManager: sidechain has ceased!")
          loadCswWitness()
        } else {
          // In case of state rollback because of MC fork where SC is alive again.
          log.info("CswManager: sidechain is alive again!")
          cswWitnessHolderOpt = None
          proofsInQueue.clear()
          generatedProofsMap.clear()
          proofInProcessOpt.foreach(inProcess => {
            proofInProcessOpt = Some(ProofInProcess(inProcess.boxId, inProcess.senderAddress, isCancelled = true))
          })
        }
      }
  }

  private def onGetCeasedStatus: Receive = {
    case GetCeasedStatus =>
      sender() ! hasSidechainCeased
  }

  private def onGetBoxNullifier: Receive = {
    case GetBoxNullifier(boxId: Array[Byte]) =>
      if(!hasSidechainCeased) {
        sender() ! Failure(new IllegalStateException("Sidechain is alive."))
      } else {
        findCswData(boxId) match {
          case Some(cswData) => sender() ! Success(cswData.getNullifier)
          case None => sender() ! Failure(new IllegalArgumentException("Box was not found for given box id."))
        }
      }
  }

  private def onGetCswBoxIds: Receive = {
    case GetCswBoxIds =>
      val cswBoxIds: Seq[Array[Byte]] = cswWitnessHolderOpt.map(cswWitnessHolder => {
        (cswWitnessHolder.utxoCswDataMap.keys ++ cswWitnessHolder.ftCswDataMap.keys).map(_.data).toSeq
      }).getOrElse(Seq())

      sender() ! cswBoxIds
  }

  private def onGenerateCswProof: Receive = {
    case GenerateCswProof(boxId: Array[Byte], senderAddress: String) =>
      if (!hasSidechainCeased) {
        sender() ! SidechainIsAlive
      } else if (!isValidSenderAddress(senderAddress)) {
        sender() ! InvalidAddress
      } else {
        val proofInfo: CswProofInfo = getProofInfo(boxId)
        // Note: CSW proof result depends on the MC sender
        val isSenderKnown = proofInfo.senderAddress.contains(senderAddress)
        proofInfo.status match {
          case Absent =>
            // Check if we have box related CSW data
            if (findCswData(boxId).isDefined) {
              addProofToQueue(boxId, senderAddress)
              self ! TryToScheduleProofGeneration
              sender() ! ProofGenerationStarted
            } else {
              sender() ! NoProofData
            }
          case InQueue | InProcess =>
            if (isSenderKnown)
              sender() ! ProofGenerationInProcess
            else {
              // In case a new request arrived with different sender address -> reschedule proof generation.
              removeProofInfo(boxId)
              addProofToQueue(boxId, senderAddress)
              self ! TryToScheduleProofGeneration
              sender() ! ProofGenerationStarted
            }
          case Generated =>
            if (isSenderKnown)
              sender() ! ProofCreationFinished
            else {
              // In case a new request arrived with different sender address ->
              // remove existing results and schedule proof generation.
              removeProofInfo(boxId)
              addProofToQueue(boxId, senderAddress)
              self ! TryToScheduleProofGeneration
              sender() ! ProofGenerationStarted
            }
        }
      }
  }

  private def onGetCswInfo: Receive = {
    case GetCswInfo(boxId: Array[Byte]) =>
      if (!hasSidechainCeased) {
        sender() ! Failure(new IllegalStateException("Sidechain is alive."))
      } else {
        cswWitnessHolderOpt match {
          case Some(cswWitnessHolder) => {
            findCswData(boxId) match {
              case Some(data: CswData) =>
                sender() ! Success(CswInfo(data.getClass.getSimpleName, data.amount, params.sidechainId, data.getNullifier,
                  getProofInfo(boxId), cswWitnessHolder.lastActiveCertOpt.map(_.certDataHash), cswWitnessHolder.mcbScTxsCumComEnd))
              case None =>
                sender() ! Failure(new IllegalArgumentException("CSW info was not found for given box id."))
            }
          }
          case None =>
            sender() ! Failure(new IllegalStateException("No CSW witness data defined."))
        }
      }
  }

  private def tryScheduleProofGeneration: Receive = {
    case TryToScheduleProofGeneration =>
      // Emit next proof generation only in case when no other one in process
      if(proofInProcessOpt.isEmpty && proofsInQueue.nonEmpty) {
        val inQueue = proofsInQueue.remove(0)
        findCswData(inQueue.boxId.data) match {
          case Some(data) =>
            proofInProcessOpt = Some(ProofInProcess(inQueue.boxId, inQueue.senderAddress))
            // Run the time consuming part of proof generation in a background
            // to unlock the Actor message queue for another requests.
            new Thread(new Runnable() {
              override def run(): Unit = {
                data match {
                  case ft: ForwardTransferCswData => // TODO: execute FT specific proof generation
                  case utxo: UtxoCswData => // TODO: execute utxo specific proof generation
                }
                // TODO: tmp code
                Thread.sleep(10 * 1000)
                self ! CswProofSuccessfullyGenerated(new Array[Byte](100))
              }
            })
          case None =>
            log.error("CswManager: Can't find CSW witness for proof generation.")
            self ! TryToScheduleProofGeneration
        }
      }
  }

  private def processProofGenerationResults: Receive = {
    case CswProofSuccessfullyGenerated(proof: Array[Byte]) =>
      proofInProcessOpt match {
        case Some(proofInProcess) =>
          if (!proofInProcess.isCancelled)
            generatedProofsMap(new ByteArrayWrapper(proofInProcess.boxId)) = CswProofInfo(Generated, Some(proof), Some(proofInProcess.senderAddress))
          proofInProcessOpt = None
        case None =>
          log.error("CswManager: inconsistent proof in process state.")
      }
      self ! TryToScheduleProofGeneration

    case CswProofFailed =>
      proofInProcessOpt = None
      self ! TryToScheduleProofGeneration
  }

  private def findCswData(boxId: Array[Byte]): Option[CswData] = {
    val id = new ByteArrayWrapper(boxId)
    cswWitnessHolderOpt.flatMap(cswWitnessHolder => {
      cswWitnessHolder.ftCswDataMap.get(id).orElse(cswWitnessHolder.utxoCswDataMap.get(id)).map(_.asInstanceOf[CswData])
    })
  }

  private def getProofInfo(boxId: Array[Byte]): CswProofInfo = {
    val id = new ByteArrayWrapper(boxId)

    generatedProofsMap.get(id).foreach(info => return info)

    proofsInQueue.find(entry => id.equals(entry.boxId)).foreach(entry => {
      return CswProofInfo(InQueue, None, Some(entry.senderAddress))
    })

    proofInProcessOpt.foreach(inProcess => {
      if (id.equals(inProcess.boxId))
        return CswProofInfo(InProcess, None, Some(inProcess.senderAddress))
    })

    CswProofInfo(Absent, None, None)
  }

  private def removeProofInfo(boxId: Array[Byte]): Unit = {
    val id = new ByteArrayWrapper(boxId)

    generatedProofsMap.remove(id)
    val idxToRemove = proofsInQueue.indexWhere(entry => id.equals(entry.boxId))
    if(idxToRemove != -1)
      proofsInQueue.remove(idxToRemove)

    // Mark proof generation cancelled if boxId has matched.
    proofInProcessOpt.foreach(inProcess => {
      if(id.equals(inProcess.boxId))
        proofInProcessOpt = Some(ProofInProcess(inProcess.boxId, inProcess.senderAddress, isCancelled = true))
    })
  }

  private def addProofToQueue(boxId: Array[Byte], senderAddress: String): Unit = {
    proofsInQueue.append(ProofInQueue(new ByteArrayWrapper(boxId), senderAddress))
  }

  private def loadCswWitness(): Unit = {
    val checkAsFuture = (sidechainNodeViewHolderRef ? GetDataFromCurrentView(loadCswWitnessMessage)).asInstanceOf[Future[CswWitnessHolder]]
    checkAsFuture.onComplete {
      case Success(res) =>
        cswWitnessHolderOpt = Some(res)
      case Failure(ex) =>
        log.error("CswManager: Failed to load CSW witness data: " + ex)
        context.stop(self)
    }
  }

  //                                                  CSW FTs
  //                                           ------------------
  // Epoch:               0       1       2       3       4       5
  //                  |=======|=======|=======|=======|=======|=======|
  // Cert(ref epoch):	          C1(0)   C2(1)   C3(2)   C4(3)     X
  //                                             ^       ^        ^
  //                                          active  reverted  ceased
  // C3(2) - last active cert
  // (2) - UTXO CSW epoch number
  // (3,4,5) - FTs CSW epoch numbers
  private def loadCswWitnessMessage(sidechainNodeView: View): CswWitnessHolder = {
    val history: SidechainHistory = sidechainNodeView.history
    val state: SidechainState = sidechainNodeView.state
    val wallet: SidechainWallet = sidechainNodeView.vault

    val withdrawalEpochNumber: Int = state.getWithdrawalEpochInfo.epoch
    // Get UTXO CSW data for all wallet known boxes being a part of the last active certificate UTXO merkle tree.
    // In case of sidechain has ceased before at least one active certificate appears -> no UTXO CSW data expected.
    val lastActiveCertReferencedEpoch = withdrawalEpochNumber - 3
    val utxoCswDataMap: Map[ByteArrayWrapper, UtxoCswData] = wallet.getCswData(lastActiveCertReferencedEpoch).flatMap(_ match {
      case utxo: UtxoCswData => Some(new ByteArrayWrapper(utxo.boxId) -> utxo)
      case _ => None
    }).toMap

    // Get FT CSW data for all wallet known boxes which appeared after last active certificate referenced epoch had finished.
    val ftWithdrawalEpochs = Seq(
      withdrawalEpochNumber,      // For FTs appeared in current epoch before SC has ceased
      withdrawalEpochNumber - 1,  // For previous epoch FTs, since no certificate appeared referencing to that epoch
      withdrawalEpochNumber - 2   // For two epoch before FTs, since certificate (if exists) was reverted/disabled.
    )

    val ftCswDataMap: Map[ByteArrayWrapper, ForwardTransferCswData] = ftWithdrawalEpochs.flatMap(epoch => wallet.getCswData(epoch)).flatMap(_ match {
      case ft: ForwardTransferCswData => Some(new ByteArrayWrapper(ft.boxId) -> ft)
      case _ => None
    }).toMap

    val lastActiveCertOpt = state.certificate(lastActiveCertReferencedEpoch)

    val mcbScTxsCumComStart: Array[Byte] = new Array[Byte](0) // TODO: get cumulative com tree hash of the last mc block 3 epochs before
    val scTxsComHashes: Seq[Array[Byte]] = Seq() // TODO: get up to RANGE_SIZE com tree hashes from the first mc block 2 epochs before to the ceased height block
    val mcbScTxsCumComEnd: Array[Byte] = new Array[Byte](0) // TODO: get cumulative com tree hash of the last mc block before ceasing

    CswWitnessHolder(utxoCswDataMap, ftCswDataMap, lastActiveCertOpt, mcbScTxsCumComStart, scTxsComHashes, mcbScTxsCumComEnd)
  }

  private def isValidSenderAddress(senderAddress: String): Boolean = {
    try {
      BytesUtils.fromHorizenPublicKeyAddress(senderAddress, params)
      true
    } catch {
      case e: IllegalArgumentException =>
        log.error(s"CswManager: Invalid sender address: $senderAddress")
        false
    }
  }
}


object CswManager {
  case class ProofInQueue(boxId: ByteArrayWrapper, senderAddress: String)
  case class ProofInProcess(boxId: ByteArrayWrapper, senderAddress: String, isCancelled: Boolean = false)

  // Public interface
  object ReceivableMessages {
    case object GetCeasedStatus
    case object GetCswBoxIds
    case class GetBoxNullifier(boxId: Array[Byte])
    case class GenerateCswProof(boxId: Array[Byte], senderAddress: String)
    case class GetCswInfo(boxId: Array[Byte])
  }

  // Private interface
  private object InternalReceivableMessages {
    case object TryToScheduleProofGeneration
    case class CswProofSuccessfullyGenerated(proof: Array[Byte])
    case object CswProofFailed
  }

  // Responses interface
  object Responses {
    sealed trait ProofStatus
    case object Absent extends ProofStatus
    case object InQueue extends ProofStatus
    case object InProcess extends ProofStatus
    case object Generated extends ProofStatus

    case class CswProofInfo(status: ProofStatus,
                            scProof: Option[Array[Byte]],
                            senderAddress: Option[String])

    case class CswInfo(cswType: String, // pure class name
                       amount: Long,
                       scId: Array[Byte],
                       nullifier: Array[Byte],
                       proofInfo: CswProofInfo,
                       activeCertData: Option[Array[Byte]],
                       ceasingCumScTxCommTree: Array[Byte])

    sealed trait GenerateCswProofStatus
    case object SidechainIsAlive extends GenerateCswProofStatus           // Sidechain is still alive
    case object InvalidAddress extends GenerateCswProofStatus             // Sender address has invalid value: MC taddress expected.
    case object NoProofData extends GenerateCswProofStatus                // Information for given box id is missed
    case object ProofGenerationStarted extends GenerateCswProofStatus     // Started proof generation, was not started of present before
    case object ProofGenerationInProcess extends GenerateCswProofStatus   // Proof generation was started before, still in process
    case object ProofCreationFinished extends GenerateCswProofStatus      // Proof is ready
  }
}


object CswManagerRef {
  def props(settings: SidechainSettings, params: NetworkParams, sidechainNodeViewHolderRef: ActorRef)
           (implicit ec: ExecutionContext) : Props =
    Props(new CswManager(settings, params, sidechainNodeViewHolderRef))

  def apply(settings: SidechainSettings, params: NetworkParams, sidechainNodeViewHolderRef: ActorRef)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, params, sidechainNodeViewHolderRef))

  def apply(name: String, settings: SidechainSettings,params: NetworkParams, sidechainNodeViewHolderRef: ActorRef)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, params, sidechainNodeViewHolderRef), name)
}
