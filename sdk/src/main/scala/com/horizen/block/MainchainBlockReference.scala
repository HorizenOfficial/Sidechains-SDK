package com.horizen.block

import java.util

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.horizen.box.Box
import com.horizen.params.NetworkParams
import com.horizen.proposition.Proposition
import com.horizen.transaction.mainchain.{BwtRequest, ForwardTransfer, SidechainCreation, SidechainRelatedMainchainOutput}
import com.horizen.serialization.Views
import sparkz.core.serialization.BytesSerializable
import com.horizen.transaction.{MC2SCAggregatedTransaction, Transaction}
import com.horizen.transaction.exception.TransactionSemanticValidityException
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, CompactSize}
import sparkz.core.serialization.SparkzSerializer
import scorex.util.serialization.{Reader, Writer}
import com.horizen.validation.{InconsistentMainchainBlockReferenceDataException, InvalidMainchainDataException}
import scorex.util.ScorexLogging

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}


// Mainchain Block structure:
//
// Field                Description                                             Size
// Block header         consists of 9 items (see @MainchainHeader)              1487+32 bytes
// Transaction counter  positive integer (number of transactions in block)      1-9 bytes
// Transactions         the (non empty) list of transactions                    depends on <Transaction counter>

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("hash", "hashHex"))
case class MainchainBlockReference(
                    header: MainchainHeader,
                    data: MainchainBlockReferenceData
                    )
  extends BytesSerializable
{

  override type M = MainchainBlockReference

  override def serializer: SparkzSerializer[MainchainBlockReference] = MainchainBlockReferenceSerializer

  def semanticValidity(params: NetworkParams): Try[Unit] = Try {
    // Check that header is valid.
    header.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => throw e
    }

    // Check that header hash and data hash are the same.
    if(!data.headerHash.sameElements(header.hash))
      throw new InvalidMainchainDataException("MainchainBlockReferenceData header hash and MainchainHeader hash are different.")

    if (header.version != MainchainBlockReference.SC_CERT_BLOCK_VERSION) {
      if (data.sidechainRelatedAggregatedTransaction.isDefined ||
          data.topQualityCertificate.isDefined ||
          data.lowerCertificateLeaves.nonEmpty ||
          data.existenceProof.isDefined ||
          data.absenceProof.isDefined) {
        throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} is inconsistent to MainchainHeader. " +
          s"MainchainBlock without SC support should have no SC related data.")
      }
      return Success(Unit) // No proof checks expected
    }

    val sidechainId = new ByteArrayWrapper(params.sidechainId)

    // Checks if we have proof defined - current sidechain was mentioned in MainchainBlockReference.
    if (data.existenceProof.isDefined) {
      // Check for defined transaction and/or certificate.
      if (data.sidechainRelatedAggregatedTransaction.isEmpty && data.topQualityCertificate.isEmpty && data.lowerCertificateLeaves.isEmpty)
        throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} is inconsistent to MainchainHeader")

      // Check for absence proof.
      if (data.absenceProof.isDefined)
        throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} is inconsistent to MainchainHeader")

      // Check top quality certificate custom fields.
      data.topQualityCertificate.foreach(cert => {
        if (params.scCreationBitVectorCertificateFieldConfigs.size != cert.bitVectorCertificateFields.size) {
          throw new InvalidMainchainDataException(s"MainchainBlockReferenceData ${header.hashHex} Top quality certificate " +
            s"bitvectors number is inconsistent to Sc Creation info.")
        }
        for (i <- cert.bitVectorCertificateFields.indices) {
          // Note: bitVectorSizeBits must be transformed to bytes first. Considering the protocol we are sure that bit size % 8 == 0.
          if (cert.bitVectorCertificateFields(i).tryMerkleRootBytesWithCheck(BytesUtils.getBytesFromBits(params.scCreationBitVectorCertificateFieldConfigs(i).getBitVectorSizeBits)).isFailure)
            throw new InvalidMainchainDataException(s"MainchainBlockReferenceData ${header.hashHex} Top quality certificate " +
              s"bitvectors data length is invalid.")
        }
      })

      if (data.sidechainRelatedAggregatedTransaction.isDefined) {
        try {
          data.sidechainRelatedAggregatedTransaction.get.semanticValidity()
        }
        catch {
          case e: TransactionSemanticValidityException =>
            throw new InvalidMainchainDataException(s"MainchainBlockReferenceData ${header.hashHex} AggTx check error: ${e.getMessage}.")
        }
      }

      val commitmentTree = data.commitmentTree(sidechainId.data, params.sidechainCreationVersion)
      val scCommitmentOpt = commitmentTree.getSidechainCommitment(sidechainId.data)
      commitmentTree.free()

      if (scCommitmentOpt.isEmpty)
        throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} is inconsistent to MainchainHeader hashScTxsCommitment")

      if (!SidechainCommitmentTree.verifyExistenceProof(scCommitmentOpt.get, data.existenceProof.get, header.hashScTxsCommitment))
        throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} is inconsistent to MainchainHeader hashScTxsCommitment")
    } else { // Current sidechain was not mentioned in MainchainBlockReference.
      // Check for empty transaction and certificates.
      if (data.sidechainRelatedAggregatedTransaction.isDefined || data.topQualityCertificate.isDefined || data.lowerCertificateLeaves.nonEmpty)
        throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} is inconsistent to MainchainHeader")

      // Check for absence proof to be defined.
      if (data.absenceProof.isEmpty)
        throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} is inconsistent to MainchainHeader")

      if (!SidechainCommitmentTree.verifyAbsenceProof(sidechainId.data, data.absenceProof.get, header.hashScTxsCommitment))
        throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} is inconsistent to MainchainHeader")
    }
  }
}

object MainchainBlockReference extends ScorexLogging {
  val MAX_MAINCHAIN_BLOCK_SIZE: Int = 4000000 // 4Mb since SC fork activated
  val SC_CERT_BLOCK_VERSION = 3

  def create(mainchainBlockBytes: Array[Byte], params: NetworkParams, versionsManager: SidechainsVersionsManager): Try[MainchainBlockReference] = {
    require(mainchainBlockBytes.length < MAX_MAINCHAIN_BLOCK_SIZE)
    require(params.sidechainId.length == 32)

    val tryBlock: Try[MainchainBlockReference] = parseMainchainBlockBytes(mainchainBlockBytes) match {
      case Success((header, mainchainTxs, certificates, blockSize)) =>
        if(blockSize < mainchainBlockBytes.length)
          throw new IllegalArgumentException("Input data corrupted. There are unprocessed %d bytes.".format(mainchainBlockBytes.length - blockSize))

        if (header.version != SC_CERT_BLOCK_VERSION) {
          val data: MainchainBlockReferenceData = MainchainBlockReferenceData(header.hash, None, None, None, Seq(), None)
          return Success(MainchainBlockReference(header, data))
        }

        // Calculate ScTxsCommitment
        var scIds: Set[ByteArrayWrapper] = Set[ByteArrayWrapper]()

        val sidechainId = new ByteArrayWrapper(params.sidechainId)
        val commitmentTree = new SidechainCommitmentTree()

        // Collect all CSW inputs
        val cswInputs: ListBuffer[MainchainTxCswCrosschainInput] = ListBuffer()
        for(tx <- mainchainTxs)
          cswInputs.appendAll(tx.cswInputs)

        val sidechainRelatedCswInputs: Map[ByteArrayWrapper, Seq[MainchainTxCswCrosschainInput]] =
          cswInputs.groupBy(input => new ByteArrayWrapper(input.sidechainId))

        scIds = scIds ++ sidechainRelatedCswInputs.keys

        // cctp CommitmentTree
        cswInputs.foreach(input => commitmentTree.addCswInput(input))

        // Collect all sidechain related outputs
        val crosschainOutputs: ListBuffer[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]] = ListBuffer()
        for(tx <- mainchainTxs)
          crosschainOutputs.appendAll(getSidechainRelatedTransactionsOutputs(tx))

        val sidechainRelatedCrosschainOutputs: Map[ByteArrayWrapper, Seq[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]]] =
          crosschainOutputs.groupBy(output => new ByteArrayWrapper(output.sidechainId()))

        scIds = scIds ++ sidechainRelatedCrosschainOutputs.keys
        crosschainOutputs.foreach{
          case sc: SidechainCreation => commitmentTree.addSidechainCreation(sc);
          case ft: ForwardTransfer => commitmentTree.addForwardTransfer(ft);
          case btr: BwtRequest => commitmentTree.addBwtRequest(btr);
        }

        val certScIds = certificates.map(c => new ByteArrayWrapper(c.sidechainId)).distinct
        scIds = scIds ++ certScIds

        val scVersions = versionsManager.getVersions(certScIds)
        certificates.foreach(cert => commitmentTree.addCertificate(cert, scVersions(new ByteArrayWrapper(cert.sidechainId))))

        val mc2scTransaction: Option[MC2SCAggregatedTransaction] =
          sidechainRelatedCrosschainOutputs.get(sidechainId).map(outputs => new MC2SCAggregatedTransaction(outputs.asJava, MC2SCAggregatedTransaction.MC2SC_AGGREGATED_TRANSACTION_VERSION))
        // Certificates for a given sidechain are ordered by quality: from lowest to highest.
        // So get the last sidechain related certificate if present
        val topQualityCertificate: Option[WithdrawalEpochCertificate] = certificates.reverse.find(c => util.Arrays.equals(c.sidechainId, sidechainId.data))
        // Get lower quality cert leaves if present.
        val certLeaves = commitmentTree.getCertLeaves(sidechainId.data)
        val lowerCertificateLeaves: Seq[Array[Byte]] = if(certLeaves.isEmpty) Seq() else certLeaves.init

        val data: MainchainBlockReferenceData =
          if (scIds.contains(sidechainId)) {
            val scExistenceProof = commitmentTree.getExistenceProof(sidechainId.data);
            MainchainBlockReferenceData(header.hash,
              mc2scTransaction,
              scExistenceProof,
              None,
              lowerCertificateLeaves,
              topQualityCertificate)
          } else {
            val scAbsenceProof = commitmentTree.getAbsenceProof(sidechainId.data);
            MainchainBlockReferenceData(header.hash,
              None,
              None,
              scAbsenceProof,
              Seq(),
              None)
          }

        commitmentTree.free()
        Success(MainchainBlockReference(header, data))

      case Failure(e) =>
        Failure(e)
    }

    if(tryBlock.isFailure)
      tryBlock
    else {
      tryBlock.get.semanticValidity(params).get
      tryBlock
    }
  }

  private def getSidechainRelatedTransactionsOutputs(tx: MainchainTransaction): Seq[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]] = {
    var indexInTx: Int = -1
    tx.getCrosschainOutputs.map(output => {
      indexInTx += 1
      output match {
        case creationOutput: MainchainTxSidechainCreationCrosschainOutput =>
          new SidechainCreation(creationOutput, tx.hash, indexInTx)
        case forwardTransferOutput: MainchainTxForwardTransferCrosschainOutput =>
          new ForwardTransfer(forwardTransferOutput, tx.hash, indexInTx)
        case bwtRequestOutput: MainchainTxBwtRequestCrosschainOutput =>
          new BwtRequest(bwtRequestOutput, tx.hash, indexInTx)
      }
    })
  }

  // Try to parse Mainchain block and return MainchainHeader, Transactions, Certificates
  // and the actual size of the parsed block.
  def parseMainchainBlockBytes(mainchainBlockBytes: Array[Byte]):
    Try[(MainchainHeader, Seq[MainchainTransaction], Seq[WithdrawalEpochCertificate], Int)] = Try {
    var offset: Int = 0

    MainchainHeader.create(mainchainBlockBytes, offset) match {
      case Success(header) =>
        offset += header.mainchainHeaderBytes.length

        val transactionsCount: CompactSize = BytesUtils.getCompactSize(mainchainBlockBytes, offset)
        offset += transactionsCount.size()

        // parse transactions
        var transactions: Seq[MainchainTransaction] = Seq[MainchainTransaction]()

        while (transactions.size < transactionsCount.value()) {
          val tx: MainchainTransaction = MainchainTransaction.create(mainchainBlockBytes, offset).get
          transactions = transactions :+ tx
          offset += tx.size
        }

        var certificates: Seq[WithdrawalEpochCertificate] = Seq[WithdrawalEpochCertificate]()

        // Parse certificates only if version is the same as specified and there is bytes to parse.
        if (header.version == SC_CERT_BLOCK_VERSION) {
            val certificatesCount: CompactSize = BytesUtils.getCompactSize(mainchainBlockBytes, offset)
            offset += certificatesCount.size()

            while (certificates.size < certificatesCount.value()) {
              log.debug(s"Parse Mainchain certificate: ${BytesUtils.toHexString(util.Arrays.copyOfRange(mainchainBlockBytes, offset, mainchainBlockBytes.length))}")
              val c: WithdrawalEpochCertificate = WithdrawalEpochCertificate.parse(mainchainBlockBytes, offset)
              certificates = certificates :+ c
              offset += c.size
            }
        }

        (header, transactions, certificates, offset)

      case Failure(e) =>
        throw e
    }
  }
}

object MainchainBlockReferenceSerializer extends SparkzSerializer[MainchainBlockReference] {
  override def serialize(obj: MainchainBlockReference, w: Writer): Unit = {
    w.putInt(obj.header.bytes.length)
    w.putBytes(obj.header.bytes)

    w.putInt(obj.data.bytes.length)
    w.putBytes(obj.data.bytes)
  }

  override def parse(r: Reader): MainchainBlockReference = {
    val headerSize: Int = r.getInt()
    val header: MainchainHeader = MainchainHeaderSerializer.parseBytes(r.getBytes(headerSize))

    val dataSize: Int = r.getInt()
    val data: MainchainBlockReferenceData = MainchainBlockReferenceDataSerializer.parseBytes(r.getBytes(dataSize))

    MainchainBlockReference(header, data)
  }
}
