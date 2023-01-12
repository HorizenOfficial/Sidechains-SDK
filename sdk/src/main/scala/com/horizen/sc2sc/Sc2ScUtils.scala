package com.horizen.sc2sc

import com.horizen.{AbstractHistory, AbstractState, SidechainHistory}

import scala.collection.JavaConverters._
import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase, WithdrawalEpochCertificate}
import com.horizen.certnative.WithdrawalCertificate
import com.horizen.chain.AbstractFeePaymentsInfo
import com.horizen.cryptolibprovider.{CommonCircuit, CryptoLibProvider, Sc2scCircuit}
import com.horizen.librustsidechains.FieldElement
import com.horizen.merkletreenative.MerklePath
import com.horizen.params.NetworkParams
import com.horizen.storage.AbstractHistoryStorage
import com.horizen.transaction.Transaction
import scala.compat.java8.OptionConverters._
import scala.util.{Success, Try}

trait Sc2ScUtils[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  MS <: AbstractState[TX, H, PM, MS]
] {

  type FPI <: AbstractFeePaymentsInfo
  type HSTOR <: AbstractHistoryStorage[PM, FPI, HSTOR]
  type HIS <: AbstractHistory[TX, H, PM, FPI, HSTOR, HIS]

  var sc2scCircuitFunctions :  Sc2scCircuit  = CryptoLibProvider.sc2scCircuitFunctions
  var commonCircuitFunctions :  CommonCircuit  = CryptoLibProvider.commonCircuitFunctions

  /**
   * Get all the the additional data to be inserted in a certificate for sidechain2sidechain pourpuses
   */
  def getDataForCertificateCreation(epoch: Int, state: MS, params: NetworkParams): Sc2ScDataForCertificate = {
    val crossChainMessages: Seq[CrossChainMessage] = state.crossChainMessages(epoch)
    val messageRootHash: Array[Byte] = sc2scCircuitFunctions.getCrossChainMessageTreeRoot(crossChainMessages.toList.asJava);

    val previousCertificateHash : Option[Array[Byte]] = state.certificate(epoch - 1) match {
      case None => Option.empty
      case Some(cert) => Some(CryptoLibProvider.commonCircuitFunctions.getCertDataHash(cert, params.sidechainCreationVersion))
    }
    Sc2ScDataForCertificate(messageRootHash,previousCertificateHash)
  }

  /**
   * Giving a CrossChainMessage previously published on this sidechain, this method will build its redeem message
   */
  def buildRedeemMessage(sourceMessage: CrossChainMessage, state: MS, history: HIS, params: NetworkParams): Try[CrossChainRedeemMessage] = {
    //check the message has been previously posted and we are in the correct epoch
    val currentEpoch = state.getCurrentConsensusEpochInfo._2.epoch
    val messageHash = sc2scCircuitFunctions.getCrossChainMessageHash(sourceMessage)

    state.getCrossChainMessageHashEpoch(messageHash) match {
      case None => throw new RuntimeException("Message was not found inside state")
      case Some(messagePostedEpoch) =>
        if (currentEpoch < messagePostedEpoch+2) {
          throw new RuntimeException("Unable to build redeem message, epoch too recent")
        }else{
          //collect all the data needed for proof creation
          val messageMerklePath : MerklePath = sc2scCircuitFunctions.gerCrossChainMessageMerklePath(
              state.crossChainMessages(messagePostedEpoch).asJava, sourceMessage);




          val topCertInfos =  getTopCertInfoByEpoch(messagePostedEpoch, state, history).getOrElse(
            throw new RuntimeException("Unable to build certificate associated with this message epoch")
          )
          val nextTopCertInfos = getTopCertInfoByEpoch(messagePostedEpoch+1, state, history).getOrElse(
            throw new RuntimeException("Unable to build certificate associated with this message epoch+1")
          )
          val topCertMerklePath = MerklePath.deserialize(topCertInfos.merklePath)
          val topCertscCommitmentRoot = FieldElement.deserialize(topCertInfos.scCommitmentRoot)
          val nextCertMerklePath = MerklePath.deserialize(nextTopCertInfos.merklePath)
          val nextCertscCommitmentRoot = FieldElement.deserialize(nextTopCertInfos.scCommitmentRoot)


          //compute proof
          val proof :Array[Byte]= sc2scCircuitFunctions.createRedeemProof(
             messageHash,
             messageMerklePath,
             CommonCircuit.createWithdrawalCertificate(topCertInfos.certificate, params.sidechainCreationVersion),
             topCertMerklePath,
             topCertscCommitmentRoot,
             CommonCircuit.createWithdrawalCertificate(nextTopCertInfos.certificate, params.sidechainCreationVersion),
             nextCertMerklePath,
             nextCertscCommitmentRoot,
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
    }
  }

  private def getTopCertInfoByEpoch(epoch: Int, state: MS, history: HIS): Option[TopQualityCertificateInfos] ={
    state.getTopCertificateMainchainHash(epoch) match {
      case Some(mainchainHeaderHash) => {
        history.getMainchainBlockReferenceByHash(mainchainHeaderHash).asScala match {
          case Some(ele) => {
            ele.data.topQualityCertificate match {
              case Some(topCert) =>
                val certPath: Array[Byte] = null //TODO: add this method: brefData.commitmentTree(sidechainId, sidechainCreationVersion).getCertMerklePath(topCert).get
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

