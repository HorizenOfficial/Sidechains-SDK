package io.horizen.sc2sc

import com.horizen.commitmenttreenative.CommitmentTree
import com.horizen.merkletreenative.MerklePath
import io.horizen.AbstractState
import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase, WithdrawalEpochCertificate}
import io.horizen.cryptolibprovider.{CommonCircuit, CryptoLibProvider, Sc2scCircuit}
import io.horizen.history.AbstractHistory
import io.horizen.params.NetworkParams
import io.horizen.transaction.Transaction

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.util.{Success, Try, Using}

trait Sc2ScUtils[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  MS <: AbstractState[TX, H, PM, MS],
  HIS <: AbstractHistory[TX, H, PM, _, _, _]
] {

  var sc2scCircuitFunctions: Sc2scCircuit = CryptoLibProvider.sc2scCircuitFunctions
  var commonCircuitFunctions: CommonCircuit = CryptoLibProvider.commonCircuitFunctions

  /**
   * Get all the the additional data to be inserted in a certificate for sidechain2sidechain pourpuses
   * Note: Certificates are searched in the history and not using state.certificate() because in the latter
   * we keep only the most recent ones.
   */
  def getDataForCertificateCreation(epoch: Int, state: MS, history: HIS, params: NetworkParams): Sc2ScDataForCertificate = {
    val crossChainMessages: Seq[CrossChainMessage] = state.getCrossChainMessages(epoch)
    Using.resource(
      sc2scCircuitFunctions.initMerkleTree()
    ) { tree => {
      sc2scCircuitFunctions.insertMessagesInMerkleTree(tree, crossChainMessages.asJava)
      val messageRootHash: Array[Byte] = sc2scCircuitFunctions.getCrossChainMessageTreeRoot(tree);
      val previousCertificateHash: Option[Array[Byte]] = getTopCertInfoByEpoch(epoch - 1, state, history, calculateMerklePath = false) match {
        case Some(certInfo) => Some(CryptoLibProvider.commonCircuitFunctions.getCertDataHash(certInfo.certificate, params.sidechainCreationVersion))
        case _ => None
      }
      Sc2ScDataForCertificate(messageRootHash, previousCertificateHash)
    }
    }
  }

  /**
   * Giving a CrossChainMessage previously published on this sidechain, this method will build its redeem message
   */
  def buildRedeemMessage(sourceMessage: CrossChainMessage, state: MS, history: HIS, params: NetworkParams): Try[CrossChainRedeemMessage] = {
    //check the message has been previously posted and we are in the correct epoch
    val currentEpoch = state.getWithdrawalEpochInfo.epoch
    val messageHash = sc2scCircuitFunctions.getCrossChainMessageHash(sourceMessage)

    state.getCrossChainMessageHashEpoch(messageHash) match {
      case None => throw new Sc2ScException("Message was not found inside state")
      case Some(messagePostedEpoch) =>
        if (currentEpoch < messagePostedEpoch + 2) {
          throw new Sc2ScException("Unable to build redeem message, epoch too recent")
        } else {
          //collect all the data needed for proof creation
          Using.resource(
            sc2scCircuitFunctions.initMerkleTree()
          ) { tree => {
            val messages = state.getCrossChainMessages(messagePostedEpoch).asJava
            val msgLeafIndex = sc2scCircuitFunctions.insertMessagesInMerkleTreeWithIndex(tree, messages, sourceMessage)
            val messageMerklePath: MerklePath = sc2scCircuitFunctions.getCrossChainMessageMerklePath(tree, msgLeafIndex)
            val topCertInfos = getTopCertInfoByEpoch(messagePostedEpoch, state, history, calculateMerklePath = true).getOrElse(
              throw new Sc2ScException("Unable to retrieve certificate associated with this message epoch")
            )
            val nextTopCertInfos = getTopCertInfoByEpoch(messagePostedEpoch + 1, state, history, calculateMerklePath = true).getOrElse(
              throw new Sc2ScException("Unable to retrieve certificate associated with this message epoch+1")
            )
            val topCertscCommitmentRoot = topCertInfos.scCommitmentRoot
            val nextCertscCommitmentRoot = nextTopCertInfos.scCommitmentRoot

            val currWithdrawalCertificate = CommonCircuit.createWithdrawalCertificate(topCertInfos.certificate, params.sidechainCreationVersion)
            val nextWithdrawalCertificate = CommonCircuit.createWithdrawalCertificate(nextTopCertInfos.certificate, params.sidechainCreationVersion)

            val currentCt = CommitmentTree.init()
            val currCertHash = currWithdrawalCertificate.getHashBytes
            currentCt.addCertLeaf(params.sidechainId, currCertHash)

            val nextCt = CommitmentTree.init()
            val nextCertHash = nextWithdrawalCertificate.getHashBytes
            nextCt.addCertLeaf(params.sidechainId, nextCertHash)

            Using.resources(
              currentCt.getScCommitmentCertPath(params.sidechainId, currCertHash).get,
              nextCt.getScCommitmentCertPath(params.sidechainId, nextCertHash).get
            ) { (currentPath, nextPath) =>
              //compute proof
              val proof: Array[Byte] = sc2scCircuitFunctions.createRedeemProof(
                messageHash,
                topCertscCommitmentRoot,
                nextCertscCommitmentRoot,
                currWithdrawalCertificate,
                nextWithdrawalCertificate,
                currentPath,
                nextPath,
                messageMerklePath,
                params.sc2ScProvingKeyFilePath
              )

              //build and return final message
              val retMessage = new CrossChainRedeemMessageImpl(
                sourceMessage,
                CryptoLibProvider.commonCircuitFunctions.getCertDataHash(topCertInfos.certificate, params.sidechainCreationVersion),
                CryptoLibProvider.commonCircuitFunctions.getCertDataHash(nextTopCertInfos.certificate, params.sidechainCreationVersion),
                topCertInfos.scCommitmentRoot,
                nextTopCertInfos.scCommitmentRoot,
                proof
              )

              Success(retMessage)
            }
          }}
        }
    }
  }

  private def getTopCertInfoByEpoch(epoch: Int, state: MS, history: HIS, calculateMerklePath: Boolean): Option[TopQualityCertificateInfos] = {
    state.getTopCertificateMainchainHash(epoch) match {
      case Some(mainchainHeaderHash) => {
        history.getMainchainBlockReferenceByHash(mainchainHeaderHash).asScala match {
          case Some(ele) => {
            ele.data.topQualityCertificate match {
              case Some(topCert) =>
                val certPath: Array[Byte] = null
                if (calculateMerklePath) {
                  //TODO: add this method: ele.data.commitmentTree(sidechainId, sidechainCreationVersion).getCertMerklePath(topCert).get
                }
                Some(TopQualityCertificateInfos(topCert, ele.header.hashScTxsCommitment, certPath))
              case None => Option.empty
            }
          }
          case None => Option.empty
        }
      }
      case None => Option.empty
    }
  }

}

case class TopQualityCertificateInfos(
                                       certificate: WithdrawalEpochCertificate,
                                       scCommitmentRoot: Array[Byte],
                                       merklePath: Array[Byte]
                                     )

