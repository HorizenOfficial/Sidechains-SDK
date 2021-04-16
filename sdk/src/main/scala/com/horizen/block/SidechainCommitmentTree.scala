package com.horizen.block

import com.horizen.utils.ByteArrayWrapper
import com.horizen.commitmenttree.CommitmentTree
import com.horizen.commitmenttree.ScExistenceProof
import com.horizen.commitmenttree.ScAbsenceProof
import com.horizen.librustsidechains.FieldElement
import com.horizen.sigproofnative.BackwardTransfer

import scala.compat.java8.OptionConverters._
import scala.collection.JavaConverters._
import com.horizen.transaction.mainchain.{BwtRequest, ForwardTransfer, SidechainCreation}

class SidechainCommitmentTree {
  val commitmentTree = CommitmentTree.init();

  def addCswInput(csw: MainchainTxCswCrosschainInput): Unit = {
    commitmentTree.addCsw(csw.sidechainId, csw.amount, csw.nullifier, csw.pubKeyHash, csw.scProof);
  }

  def addSidechainCreation(sc: SidechainCreation): Unit = {
    val scOutput: MainchainTxSidechainCreationCrosschainOutput = sc.getScCrOutput()

    commitmentTree.addScCr(sc.sidechainId, scOutput.amount, scOutput.address, sc.withdrawalEpochLength, scOutput.customData,
      scOutput.constant.asJava, scOutput.certVk, scOutput.mbtrVk.asJava, scOutput.ceasedVk.asJava, sc.hash(), sc.transactionIndex());
  }

  def addForwardTransfer(ft: ForwardTransfer): Unit = {
    val ftOutput: MainchainTxForwardTransferCrosschainOutput = ft.getFtOutput();
    commitmentTree.addFwt(ft.sidechainId, ftOutput.amount, ftOutput.propositionBytes, ft.hash, ft.transactionIndex());
  }

  def addBwtRequest(btr: BwtRequest): Unit = {
    val btrOutput: MainchainTxBwtRequestCrosschainOutput = btr.getBwtOutput();
    commitmentTree.addBtr(btr.sidechainId, btrOutput.scFee, btrOutput.mcDestinationAddress, btrOutput.bwtRequestOutputBytes, btr.hash, btr.transactionIndex());
  }

  def addCertificate(certificate: WithdrawalEpochCertificate): Unit = {
    val sampleField:Array[Byte] = Array(
      0xffb, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
      0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
      0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
      0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x3f
    ).map(value => value.toByte);

    val btrList: Seq[BackwardTransfer] = certificate.backwardTransferOutputs.map(btrOutput => new BackwardTransfer(btrOutput.pubKeyHash, btrOutput.amount));
    commitmentTree.addCert(certificate.sidechainId, certificate.epochNumber, certificate.quality, certificate.hash, btrList.toArray, sampleField, certificate.endEpochBlockHash);
  }

  def addCertLeaf(scid: Array[Byte], leaf: Array[Byte])  = {
    commitmentTree.addCertLeaf(scid, leaf);
  }

  def getTreeCommitment(): Option[Array[Byte]] = {
    commitmentTree.getCommitment.asScala match {
      case Some(fe) => {
        val res = fe.serializeFieldElement();
        fe.freeFieldElement();
        Some(res)
      }
      case None => None
    }
  }

  def getSidechainCommitmentEntryHash(sidechainId: ByteArrayWrapper): Option[Array[Byte]] = {
    commitmentTree.getScCommitment(sidechainId.data).asScala match {
      case Some(fe) => {
        val res = fe.serializeFieldElement();
        fe.freeFieldElement();
        Some(res)
      }
      case None => None
    }
  }

  def getCertLeafs(sidechainId: Array[Byte]): Seq[Array[Byte]] = {
    val certLeafsOpt: Option[java.util.List[FieldElement]] = commitmentTree.getCrtLeaves(sidechainId).asScala;
    certLeafsOpt match {
      case Some(certList) => {
        certList.asScala.map(cert => cert.serializeFieldElement())
      }
      case None => Seq()
    }
  }

  def getExistanceProof(scid: Array[Byte]): Option[Array[Byte]] = {
    commitmentTree.getScExistenceProof(scid).asScala match {
      case Some(proof) => {
        val proofBytes = proof.serialize();
        proof.freeScExistenceProof();
        Some(proofBytes)
      }
      case None => None
    }
  }

  def getAbsenceProof(scid: Array[Byte]): Option[Array[Byte]] = {
    commitmentTree.getScAbsenceProof(scid).asScala match {
      case Some(proof) => {
        val proofBytes = proof.serialize();
        proof.freeScAbsenceProof();
        Some(proofBytes)
      }
      case None => None
    }
  }
}

object SidechainCommitmentTree {
  def verifyExistanceProof(scCommitment: Array[Byte], commitmentProof: Array[Byte], commitment: Array[Byte]): Boolean = {
    val scCommitmentFe = FieldElement.deserialize(scCommitment);
    val commitmentProofFe = ScExistenceProof.deserialize(commitmentProof);
    val commitmentFe = FieldElement.deserialize(commitment);
    val res = CommitmentTree.verifyScCommitment(scCommitmentFe, commitmentProofFe, commitmentFe);
    scCommitmentFe.freeFieldElement();
    commitmentProofFe.freeScExistenceProof();
    commitmentFe.freeFieldElement();
    res
  }

  def verifyAbsenceProof(scid: Array[Byte], absenceProof: Array[Byte], commitment: Array[Byte]): Boolean = {
    val absenceProofFe = ScAbsenceProof.deserialize(absenceProof);
    val commitmentFe = FieldElement.deserialize(commitment);
    val res = CommitmentTree.verifyScAbsence(scid, absenceProofFe, commitmentFe);
    absenceProofFe.freeScAbsenceProof();
    commitmentFe.freeFieldElement();
    res
  }
}
