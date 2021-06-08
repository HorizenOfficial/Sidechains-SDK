package com.horizen.block

import com.horizen.utils.BytesUtils
import com.horizen.commitmenttree.{CommitmentTree, ScAbsenceProof, ScExistenceProof}
import com.horizen.librustsidechains.FieldElement
import com.horizen.sigproofnative.BackwardTransfer

import scala.compat.java8.OptionConverters._
import scala.collection.JavaConverters._
import com.horizen.transaction.mainchain.{BwtRequest, ForwardTransfer, SidechainCreation}

class SidechainCommitmentTree {
  val commitmentTree: CommitmentTree = CommitmentTree.init()

  def addCswInput(csw: MainchainTxCswCrosschainInput): Boolean = {
    commitmentTree.addCsw(
      csw.sidechainId,
      csw.amount,
      csw.nullifier,
      csw.mcPubKeyHash)
  }

  // Note: we must be sure that all raw data types are passed to the CommitmentTree in LittleEndian.
  // Otherwise, the result will be different to the one in the MC.
  private def toLE(bytes: Array[Byte]): Array[Byte] = BytesUtils.reverseBytes(bytes)

  def addSidechainCreation(sc: SidechainCreation): Boolean = {
    val scOutput: MainchainTxSidechainCreationCrosschainOutput = sc.getScCrOutput
    commitmentTree.addScCr(
      sc.sidechainId,
      scOutput.amount,
      scOutput.address,
      toLE(sc.transactionHash()),
      sc.transactionIndex(),
      sc.withdrawalEpochLength,
      scOutput.mainchainBackwardTransferRequestDataLength,
      scOutput.fieldElementCertificateFieldConfigs.toArray,
      scOutput.bitVectorCertificateFieldConfigs.toArray,
      scOutput.btrFee,
      scOutput.ftMinAmount,
      scOutput.customCreationData,
      scOutput.constantOpt.asJava,
      scOutput.certVk,
      scOutput.ceasedVkOpt.asJava
    )
  }

  def addForwardTransfer(ft: ForwardTransfer): Boolean = {
    val ftOutput: MainchainTxForwardTransferCrosschainOutput = ft.getFtOutput
    commitmentTree.addFwt(
      ft.sidechainId(),
      ftOutput.amount,
      ftOutput.propositionBytes,
      toLE(ft.transactionHash()),
      ft.transactionIndex()
    )
  }

  def addBwtRequest(btr: BwtRequest): Boolean = {
    val btrOutput: MainchainTxBwtRequestCrosschainOutput = btr.getBwtOutput
    commitmentTree.addBtr(
      btr.sidechainId(),
      btrOutput.scFee,
      btrOutput.mcDestinationAddress,
      btrOutput.scRequestData,
      toLE(btr.transactionHash()),
      btr.transactionIndex()
    )
  }

  def addCertificate(certificate: WithdrawalEpochCertificate): Boolean = {
    val btrList: Seq[BackwardTransfer] = certificate.backwardTransferOutputs.map(btrOutput =>
      new BackwardTransfer(btrOutput.pubKeyHash, btrOutput.amount)
    )

    commitmentTree.addCert(
      certificate.sidechainId,
      certificate.epochNumber,
      certificate.quality,
      btrList.toArray,
      certificate.customFieldsOpt.asJava,
      certificate.endCumulativeScTxCommitmentTreeRoot,
      certificate.btrFee,
      certificate.ftMinAmount
    )
  }

  def addCertLeaf(sidechainId: Array[Byte], leaf: Array[Byte]): Boolean = {
    commitmentTree.addCertLeaf(
      sidechainId,
      leaf
    )
  }

  def getCommitment: Option[Array[Byte]] = {
    commitmentTree.getCommitment.asScala match {
      case Some(fe) => {
        val res = fe.serializeFieldElement()
        fe.freeFieldElement()
        Some(res)
      }
      case None => None
    }
  }

  def getSidechainCommitment(sidechainId: Array[Byte]): Option[Array[Byte]] = {
    commitmentTree.getScCommitment(sidechainId).asScala match {
      case Some(fe) => {
        val res = fe.serializeFieldElement()
        fe.freeFieldElement()
        Some(res)
      }
      case None => None
    }
  }

  def getCertLeafs(sidechainId: Array[Byte]): Seq[Array[Byte]] = {
    val certLeafsOpt: Option[java.util.List[FieldElement]] = commitmentTree.getCrtLeaves(sidechainId).asScala
    certLeafsOpt match {
      case Some(certList) => {
        certList.asScala.map(cert => cert.serializeFieldElement())
      }
      case None => Seq()
    }
  }

  def getExistenceProof(sidechainId: Array[Byte]): Option[Array[Byte]] = {
    commitmentTree.getScExistenceProof(sidechainId).asScala match {
      case Some(proof) => {
        val proofBytes = proof.serialize()
        proof.freeScExistenceProof()
        Some(proofBytes)
      }
      case None => None
    }
  }

  def getAbsenceProof(sidechainId: Array[Byte]): Option[Array[Byte]] = {
    commitmentTree.getScAbsenceProof(sidechainId).asScala match {
      case Some(proof) => {
        val proofBytes = proof.serialize()
        proof.freeScAbsenceProof()
        Some(proofBytes)
      }
      case None => None
    }
  }
}

object SidechainCommitmentTree {
  def verifyExistenceProof(scCommitment: Array[Byte], commitmentProof: Array[Byte], commitment: Array[Byte]): Boolean = {
    val commitmentLE = BytesUtils.reverseBytes(commitment)
    val scCommitmentFe = FieldElement.deserialize(scCommitment)
    val commitmentProofFe = ScExistenceProof.deserialize(commitmentProof)
    val commitmentFe = FieldElement.deserialize(commitmentLE)
    val res = CommitmentTree.verifyScCommitment(scCommitmentFe, commitmentProofFe, commitmentFe)
    scCommitmentFe.freeFieldElement()
    commitmentProofFe.freeScExistenceProof()
    commitmentFe.freeFieldElement()
    res
  }

  def verifyAbsenceProof(sidechainId: Array[Byte], absenceProof: Array[Byte], commitment: Array[Byte]): Boolean = {
    val commitmentLE = BytesUtils.reverseBytes(commitment)
    val absenceProofFe = ScAbsenceProof.deserialize(absenceProof)
    val commitmentFe = FieldElement.deserialize(commitmentLE)
    val res = CommitmentTree.verifyScAbsence(sidechainId, absenceProofFe, commitmentFe)
    absenceProofFe.freeScAbsenceProof()
    commitmentFe.freeFieldElement()
    res
  }
}
