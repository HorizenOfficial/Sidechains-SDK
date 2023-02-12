package com.horizen.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.horizen.block.SidechainCreationVersions.SidechainCreationVersion
import com.horizen.cryptolibprovider.utils.FieldElementUtils
import com.horizen.serialization.Views
import com.horizen.transaction.mainchain.{ForwardTransfer, SidechainCreation}
import com.horizen.transaction.{MC2SCAggregatedTransaction, MC2SCAggregatedTransactionSerializer}
import com.horizen.utils.Checker
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

import scala.collection.JavaConverters._

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("hash"))
case class MainchainBlockReferenceData(
                                        headerHash: Array[Byte],
                                        sidechainRelatedAggregatedTransaction: Option[MC2SCAggregatedTransaction],
                                        existenceProof: Option[Array[Byte]],
                                        absenceProof: Option[Array[Byte]],
                                        lowerCertificateLeaves: Seq[Array[Byte]],
                                        topQualityCertificate: Option[WithdrawalEpochCertificate]) extends BytesSerializable {
  override type M = MainchainBlockReferenceData

  override def serializer: SparkzSerializer[MainchainBlockReferenceData] = MainchainBlockReferenceDataSerializer

  override def hashCode(): Int = java.util.Arrays.hashCode(headerHash)

  override def equals(obj: Any): Boolean = {
    obj match {
      case data: MainchainBlockReferenceData => bytes.sameElements(data.bytes)
      case _ => false
    }
  }

  def commitmentTree(sidechainId: Array[Byte], version: SidechainCreationVersion): SidechainCommitmentTree = {
    val commitmentTree = new SidechainCommitmentTree()

    sidechainRelatedAggregatedTransaction.foreach(_.mc2scTransactionsOutputs().asScala.foreach {
      case sc: SidechainCreation => commitmentTree.addSidechainCreation(sc)
      case ft: ForwardTransfer => commitmentTree.addForwardTransfer(ft)
    })

    lowerCertificateLeaves.foreach(leaf => commitmentTree.addCertLeaf(sidechainId, leaf))
    topQualityCertificate.foreach(cert => commitmentTree.addCertificate(cert, version))

    commitmentTree
  }
}


object MainchainBlockReferenceDataSerializer extends SparkzSerializer[MainchainBlockReferenceData] {
  val HASH_BYTES_LENGTH: Int = 32

  override def serialize(obj: MainchainBlockReferenceData, w: Writer): Unit = {
    w.putBytes(obj.headerHash)

    w.putOption(obj.sidechainRelatedAggregatedTransaction) {case (writer: Writer, aggregatedTransaction: MC2SCAggregatedTransaction) =>
      MC2SCAggregatedTransactionSerializer.getSerializer.serialize(aggregatedTransaction, writer)
    }

    obj.existenceProof match {
      case Some(proofBytes) =>
        w.putInt(proofBytes.length)
        w.putBytes(proofBytes)
      case None =>
        w.putInt(0)
    }

    obj.absenceProof match {
      case Some(proofBytes) =>
        w.putInt(proofBytes.length)
        w.putBytes(proofBytes)
      case None =>
        w.putInt(0)
    }

    w.putInt(obj.lowerCertificateLeaves.size)
    obj.lowerCertificateLeaves.foreach(leaf => w.putBytes(leaf))

    obj.topQualityCertificate match {
      case Some(certificate) =>
        val cb = WithdrawalEpochCertificateSerializer.toBytes(certificate)
        w.putInt(cb.length)
        w.putBytes(cb)
      case _ => w.putInt(0)
    }

  }

  override def parse(r: Reader): MainchainBlockReferenceData = {
    val headerHash: Array[Byte] = Checker.readBytes(r, HASH_BYTES_LENGTH, "header hash")

    val mc2scTx: Option[MC2SCAggregatedTransaction] = r.getOption(MC2SCAggregatedTransactionSerializer.getSerializer.parse(r))

    val existenceProofSize: Int = Checker.readIntNotLessThanZero(r, "existence proof size")

    val existenceProof: Option[Array[Byte]] = {
      if (existenceProofSize > 0)
        Some(Checker.readBytes(r, existenceProofSize, "existence proof"))
      else
        None
    }

    val absenceProofSize: Int = Checker.readIntNotLessThanZero(r, "absence proof size")

    val absenceProof: Option[Array[Byte]] = {
      if (absenceProofSize > 0)
        Some(Checker.readBytes(r, absenceProofSize, "absense proof"))
      else
        None
    }

    val lowerCertificateLeavesSize: Int = Checker.readIntNotLessThanZero(r, "lower certificate leaves size")
    val lowerCertificateLeaves: Seq[Array[Byte]] = (0 until lowerCertificateLeavesSize)
      .map(_ => Checker.readBytes(r, FieldElementUtils.fieldElementLength(), "lower certificate"))

    val topQualityCertificateSize: Int = Checker.readIntNotLessThanZero(r, "top quality certificate size")
    val topQualityCertificate: Option[WithdrawalEpochCertificate] = {
      if (topQualityCertificateSize > 0)
        Some(WithdrawalEpochCertificateSerializer.parseBytes(Checker.readBytes(r, topQualityCertificateSize, "withdrawal epoch certificate")))
      else
        None
    }

    MainchainBlockReferenceData(headerHash, mc2scTx, existenceProof, absenceProof, lowerCertificateLeaves, topQualityCertificate)
  }
}