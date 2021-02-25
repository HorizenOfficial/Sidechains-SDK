package com.horizen.block

import java.util
import java.util.Arrays
import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.horizen.box.Box
import com.horizen.params.NetworkParams
import com.horizen.proposition.Proposition
import com.horizen.transaction.mainchain.{ForwardTransfer, SidechainCreation, SidechainRelatedMainchainOutput}
import com.horizen.serialization.Views
import scorex.core.serialization.BytesSerializable
import com.horizen.transaction.MC2SCAggregatedTransaction
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, MerkleTree, VarInt}
import scorex.core.serialization.ScorexSerializer
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

  override def serializer: ScorexSerializer[MainchainBlockReference] = MainchainBlockReferenceSerializer

  // TODO: check "is mutated" of merkle tree used inside
  def semanticValidity(params: NetworkParams): Try[Unit] = Try {
    // Check that header is valid.
    header.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => throw e
    }

    // Check that header hash and data hash are the same.
    if(!data.headerHash.sameElements(header.hash))
      throw new InvalidMainchainDataException("MainchainBlockReferenceData header hash and MainchainHeader hash are different.")

    if (util.Arrays.equals(header.hashScTxsCommitment, params.zeroHashBytes)) {
      // If there is not SC related outputs in MC block, then proofs, AggTx and Certificates expected to be not defined.
      if (data.mProof.isDefined ||
          data.proofOfNoData._1.isDefined ||
          data.proofOfNoData._2.isDefined ||
          data.sidechainRelatedAggregatedTransaction.isDefined ||
          data.lowerCertificateLeaves.nonEmpty ||
          data.topQualityCertificate.isDefined)
        throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} is inconsistent to MainchainHeader")
    }
    else {
      val sidechainId = new ByteArrayWrapper(params.sidechainId)

      // Checks if we have proof defined - current sidechain was mentioned in MainchainBlockReference.
      if (data.mProof.isDefined) {
        // Check for defined transaction and/or certificate.
        if (data.sidechainRelatedAggregatedTransaction.isEmpty && data.topQualityCertificate.isEmpty && data.lowerCertificateLeaves.isEmpty)
          throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} is inconsistent to MainchainHeader")

        // Check for empty neighbour proofs.
        if (data.proofOfNoData._1.isDefined || data.proofOfNoData._2.isDefined)
          throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} is inconsistent to MainchainHeader")

        val sidechainCommitment = new SidechainCommitmentEntry()

        if (data.sidechainRelatedAggregatedTransaction.isDefined) {
          // TODO: remove "isMutated" checks everywhere after rust solution integration
          val mc2scTransactionsOutputsMerkleTree = MerkleTree.createMerkleTree(
            data.sidechainRelatedAggregatedTransaction.get.mc2scTransactionsOutputs().asScala.map(_.hash()).asJava)
          if(mc2scTransactionsOutputsMerkleTree.isMutated)
            throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} AggTx outputs leads to mutated MerkleTree.")

          data.sidechainRelatedAggregatedTransaction.get.mc2scTransactionsOutputs().asScala.foreach {
            case sc: SidechainCreation => sidechainCommitment.addSidechainCreation(sc)
            case ft: ForwardTransfer => sidechainCommitment.addForwardTransfer(ft)
          }
        }

        // Add certificates in the original order
        data.lowerCertificateLeaves.foreach(leaf => sidechainCommitment.addCertLeaf(leaf))
        data.topQualityCertificate.foreach(cert => sidechainCommitment.addCertificate(cert))

        val sidechainCommitmentHash = sidechainCommitment.getCommitment(sidechainId.data)

        if (!util.Arrays.equals(header.hashScTxsCommitment, data.mProof.get.apply(sidechainCommitmentHash)))
          throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} is inconsistent to MainchainHeader hashScTxsCommitment")

        if (data.sidechainRelatedAggregatedTransaction.isDefined && !data.sidechainRelatedAggregatedTransaction.get.semanticValidity())
          throw new InvalidMainchainDataException(s"MainchainBlockReferenceData ${header.hashHex} AggTx is semantically invalid.")
      } else { // Current sidechain was not mentioned in MainchainBlockReference.
        // Check for empty transaction and certificates.
        if (data.sidechainRelatedAggregatedTransaction.isDefined || data.topQualityCertificate.isDefined || data.lowerCertificateLeaves.nonEmpty)
          throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} is inconsistent to MainchainHeader")

        // Check for at least one neighbour proof to be defined.
        if (data.proofOfNoData._1.isEmpty && data.proofOfNoData._2.isEmpty)
          throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} is inconsistent to MainchainHeader")

        // In case if both neighbours are defined, check that there are no sidechains between them.
        if (data.proofOfNoData._1.isDefined && data.proofOfNoData._2.isDefined &&
            data.proofOfNoData._2.get.merklePath.leafIndex() - 1 != data.proofOfNoData._1.get.merklePath.leafIndex())
          throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} left and right neighbours expected to be neighbour leaves in ScTxsCommitment merkle tree")

        // In case if only right neighbour is defined, it must be the leftmost leaf in the sidechains merkle tree.
        if (data.proofOfNoData._1.isEmpty && !data.proofOfNoData._2.get.merklePath.isLeftmost)
          throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} right neighbour expected to be the first leaf in ScTxsCommitment merkle tree")

        // In case if only left neighbour is defined, it must be the rightmost leaf in the sidechains merkle tree.
        if (data.proofOfNoData._2.isEmpty &&
            !data.proofOfNoData._1.get.merklePath.isRightmost(SidechainCommitmentEntry.getCommitment(data.proofOfNoData._1.get)))
          throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} left neighbour expected to be the last leaf in ScTxsCommitment merkle tree")

        data.proofOfNoData._1 match {
          case Some(leftNeighbourProof) =>
            // Compare in little-endian like in MC
            if (new ByteArrayWrapper(BytesUtils.reverseBytes(leftNeighbourProof.sidechainId)) >= new ByteArrayWrapper(BytesUtils.reverseBytes(sidechainId.data)))
              throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} left neighbour sidechain id is after current sidechain id")
            val merkleRoot = leftNeighbourProof.merklePath.apply(SidechainCommitmentEntry.getCommitment(leftNeighbourProof))
            if (!util.Arrays.equals(header.hashScTxsCommitment, merkleRoot))
              throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} left neighbour proof is inconsistent to MainchainHeader hashScTxsCommitment")
          case None =>
        }

        data.proofOfNoData._2 match {
          case Some(rightNeighbourProof) =>
            // Compare in little-endian like in MC
            if (new ByteArrayWrapper(BytesUtils.reverseBytes(rightNeighbourProof.sidechainId)) <= new ByteArrayWrapper(BytesUtils.reverseBytes(sidechainId.data)))
              throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} right neighbour sidechain id is before current sidechain id")
            val merkleRoot = rightNeighbourProof.merklePath.apply(SidechainCommitmentEntry.getCommitment(rightNeighbourProof))
            if (!util.Arrays.equals(header.hashScTxsCommitment, merkleRoot))
              throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} right neighbour proof is inconsistent to MainchainHeader hashScTxsCommitment")
          case None =>
        }
      }
    }
  }
}

object MainchainBlockReference extends ScorexLogging {
  // TO DO: check size
  val MAX_MAINCHAIN_BLOCK_SIZE: Int = 2048 * 1024 //2048K
  val SC_CERT_BLOCK_VERSION = 3

  def create(mainchainBlockBytes: Array[Byte], params: NetworkParams): Try[MainchainBlockReference] = {
    require(mainchainBlockBytes.length < MAX_MAINCHAIN_BLOCK_SIZE)
    require(params.sidechainId.length == 32)

    val tryBlock: Try[MainchainBlockReference] = parseMainchainBlockBytes(mainchainBlockBytes) match {
      case Success((header, mainchainTxs, certificates)) =>
        // Calculate ScTxsCommitment
        var scIds: Set[ByteArrayWrapper] = Set[ByteArrayWrapper]()

        val sidechainId = new ByteArrayWrapper(params.sidechainId)

        val sidechainsCommitmentTree = new SidechainsCommitmentTree()

        // Collect all sidechain related outputs
        val crosschainOutputs: ListBuffer[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]] = ListBuffer()
        for(tx <- mainchainTxs)
          crosschainOutputs.appendAll(getSidechainRelatedTransactionsOutputs(tx))

        val sidechainRelatedCrosschainOutputs: Map[ByteArrayWrapper, Seq[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]]] =
          crosschainOutputs.groupBy(output => new ByteArrayWrapper(output.sidechainId()))

        scIds = scIds ++ sidechainRelatedCrosschainOutputs.keys
        sidechainRelatedCrosschainOutputs.foreach {
          case (scId, outputs) =>
            // Update commitment tree
            outputs.foreach {
              case sc: SidechainCreation => sidechainsCommitmentTree.addSidechainCreation(scId, sc)
              case ft: ForwardTransfer => sidechainsCommitmentTree.addForwardTransfer(scId, ft)
            }
        }

        scIds = scIds ++ certificates.map(c => new ByteArrayWrapper(c.sidechainId))
        certificates.foreach(cert => sidechainsCommitmentTree.addCertificate(cert))


        val mc2scTransaction: Option[MC2SCAggregatedTransaction] =
          sidechainRelatedCrosschainOutputs.get(sidechainId).map(outputs => new MC2SCAggregatedTransaction(outputs.asJava, header.time))
        // Certificates for a given sidechain are ordered by quality: from lowest to highest.
        // So get the last sidechain related certificate if present
        val topQualityCertificate: Option[WithdrawalEpochCertificate] = certificates.reverse.find(c => util.Arrays.equals(c.sidechainId, sidechainId.data))
        // Get lower quality cert leaves if present.
        val certLeaves = sidechainsCommitmentTree.getCertLeaves(sidechainId)
        val lowerCertificateLeaves: Seq[Array[Byte]] = if(certLeaves.isEmpty) Seq() else certLeaves.init

        val data: MainchainBlockReferenceData =
          if (scIds.isEmpty) {
            MainchainBlockReferenceData(header.hash, None, None, (None, None), Seq(), None)
          } else if (scIds.contains(sidechainId)) {
            MainchainBlockReferenceData(header.hash,
              mc2scTransaction,
              sidechainsCommitmentTree.getSidechainCommitmentEntryMerklePath(sidechainId),
              (None, None),
              lowerCertificateLeaves,
              topQualityCertificate)
          } else {
            MainchainBlockReferenceData(header.hash,
              mc2scTransaction,
              None,
              sidechainsCommitmentTree.getNeighbourSidechainCommitmentEntryProofs(sidechainId),
              Seq(),
              None)
          }

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
      }
    })
  }

  // Try to parse Mainchain block and return MainchainHeader, SCMap and MainchainTransactions sequence.
  private def parseMainchainBlockBytes(mainchainBlockBytes: Array[Byte]):
    Try[(MainchainHeader, Seq[MainchainTransaction], Seq[WithdrawalEpochCertificate])] = Try {
    var offset: Int = 0

    MainchainHeader.create(mainchainBlockBytes, offset) match {
      case Success(header) =>
        offset += header.mainchainHeaderBytes.length

        val transactionsCount: VarInt = BytesUtils.getReversedVarInt(mainchainBlockBytes, offset)
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
            val certificatesCount: VarInt = BytesUtils.getReversedVarInt(mainchainBlockBytes, offset)
            offset += certificatesCount.size()

            while (certificates.size < certificatesCount.value()) {
              log.debug(s"Parse Mainchain certificate: ${BytesUtils.toHexString(util.Arrays.copyOfRange(mainchainBlockBytes, offset, mainchainBlockBytes.length))}")
              val c: WithdrawalEpochCertificate = WithdrawalEpochCertificate.parse(mainchainBlockBytes, offset)
              certificates = certificates :+ c
              offset += c.size
            }
        }

        if(offset < mainchainBlockBytes.length)
          throw new IllegalArgumentException("Input data corrupted. There are unprocessed %d bytes.".format(mainchainBlockBytes.length - offset))

        (header, transactions, certificates)

      case Failure(e) =>
        throw e
    }
  }
}

object MainchainBlockReferenceSerializer extends ScorexSerializer[MainchainBlockReference] {
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
