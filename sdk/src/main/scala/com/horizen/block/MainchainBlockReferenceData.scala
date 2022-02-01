package com.horizen.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.horizen.cryptolibprovider.FieldElementUtils
import com.horizen.serialization.Views
import com.horizen.transaction.mainchain.{ForwardTransfer, SidechainCreation}
import com.horizen.transaction.{MC2SCAggregatedTransaction, MC2SCAggregatedTransactionSerializer}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}
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

  override def serializer: ScorexSerializer[MainchainBlockReferenceData] = MainchainBlockReferenceDataSerializer

  override def hashCode(): Int = java.util.Arrays.hashCode(headerHash)

  override def equals(obj: Any): Boolean = {
    obj match {
      case data: MainchainBlockReferenceData => bytes.sameElements(data.bytes)
      case _ => false
    }
  }

  def commitmentTree(sidechainId: Array[Byte]): SidechainCommitmentTree = {
    val commitmentTree = new SidechainCommitmentTree()

    sidechainRelatedAggregatedTransaction.foreach(_.mc2scTransactionsOutputs().asScala.foreach {
      case sc: SidechainCreation => commitmentTree.addSidechainCreation(sc)
      case ft: ForwardTransfer => commitmentTree.addForwardTransfer(ft)
    })

    lowerCertificateLeaves.foreach(leaf => commitmentTree.addCertLeaf(sidechainId, leaf))
    topQualityCertificate.foreach(cert => commitmentTree.addCertificate(cert))

    commitmentTree
  }
}


object MainchainBlockReferenceDataSerializer extends ScorexSerializer[MainchainBlockReferenceData] {
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
    val headerHash: Array[Byte] = r.getBytes(HASH_BYTES_LENGTH)

    val mc2scTx: Option[MC2SCAggregatedTransaction] = r.getOption(MC2SCAggregatedTransactionSerializer.getSerializer.parse(r))

    val existenceProofSize: Int = r.getInt()

    val existenceProof: Option[Array[Byte]] = {
      if (existenceProofSize > 0)
        Some(r.getBytes(existenceProofSize))
      else
        None
    }

    val absenceProofSize: Int = r.getInt()

    val absenceProof: Option[Array[Byte]] = {
      if (absenceProofSize > 0)
        Some(r.getBytes(absenceProofSize))
      else
        None
    }

    val lowerCertificateLeavesSize: Int = r.getInt()
    val lowerCertificateLeaves: Seq[Array[Byte]] = (0 until lowerCertificateLeavesSize).map(_ => r.getBytes(FieldElementUtils.fieldElementLength()))

    val topQualityCertificateSize: Int = r.getInt()
    val topQualityCertificate: Option[WithdrawalEpochCertificate] = {
      if (topQualityCertificateSize > 0)
        Some(WithdrawalEpochCertificateSerializer.parseBytes(r.getBytes(topQualityCertificateSize)))
      else
        None
    }

    MainchainBlockReferenceData(headerHash, mc2scTx, existenceProof, absenceProof, lowerCertificateLeaves, topQualityCertificate)
  }
}