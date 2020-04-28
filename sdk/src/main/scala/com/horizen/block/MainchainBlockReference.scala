package com.horizen.block

import java.util

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonProperty, JsonView}
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.horizen.box.Box
import com.horizen.params.NetworkParams
import com.horizen.proposition.Proposition
import com.horizen.transaction.mainchain.{CertifierLock, ForwardTransfer, SidechainCreation, SidechainRelatedMainchainOutput}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import com.horizen.serialization.{JsonMerklePathOptionSerializer, JsonMerkleRootsSerializer, Views}
import scorex.core.serialization.BytesSerializable
import com.horizen.transaction.{MC2SCAggregatedTransaction, MC2SCAggregatedTransactionSerializer}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, ListSerializer, MerklePath, MerkleTree, Utils, VarInt}
import scorex.core.serialization.ScorexSerializer
import scorex.util.serialization.{Reader, Writer}
import com.google.common.primitives.{Bytes, Ints}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}
import scala.collection.mutable


// Mainchain Block structure:
//
// Field                Description                                             Size
// Blockheader          consists of 9 items (see @MainchainHeader)              1487+32 bytes
// Transaction counter  positive integer (number of transactions in block)      1-9 bytes
// Transactions         the (non empty) list of transactions                    depends on <Transaction counter>

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("hash", "hashHex"))
class MainchainBlockReference(
                    val header: MainchainHeader,
                    val sidechainRelatedAggregatedTransaction: Option[MC2SCAggregatedTransaction],
                    @JsonProperty("mproof")
                    @JsonSerialize(using = classOf[JsonMerklePathOptionSerializer])
                    val mproof: Option[MerklePath],
                    val proofOfNoData: (Option[NeighbourProof], Option[NeighbourProof]),
                    val backwardTransferCertificate: Option[MainchainBackwardTransferCertificate]
                    )
  extends BytesSerializable
{

  lazy val hash: Array[Byte] = header.hash

  lazy val hashHex: String = BytesUtils.toHexString(hash)

  override type M = MainchainBlockReference

  override def serializer: ScorexSerializer[MainchainBlockReference] = MainchainBlockReferenceSerializer

  def semanticValidity(params: NetworkParams): Boolean = {
    if (header == null || !header.semanticValidity(params))
      return false

    if (util.Arrays.equals(header.hashScTxsCommitment, params.zeroHashBytes)) {
      // If there is not SC related outputs in MC block, proofs, AggTx and Certificate expected to be not defined.
      if (mproof.isDefined ||
          proofOfNoData._1.isDefined ||
          proofOfNoData._2.isDefined ||
          sidechainRelatedAggregatedTransaction.isDefined ||
          backwardTransferCertificate.isDefined)
        return false
    }
    else {

      val sidechainId = new ByteArrayWrapper(params.sidechainId)

      //Checks if we have proof
      if (mproof.isDefined) {

        //Check for empty transaction and certificate
        if (sidechainRelatedAggregatedTransaction.isEmpty && backwardTransferCertificate.isEmpty)
          return false

        //Check for empty neighbour proofs
        if (proofOfNoData._1.isDefined || proofOfNoData._2.isDefined)
          return false

        val sidechainHashMap = new SidechainsHashMap()

        if (sidechainRelatedAggregatedTransaction.isDefined)
          sidechainHashMap.addTransactionOutputs(sidechainId,
            sidechainRelatedAggregatedTransaction.get.mc2scTransactionsOutputs().asScala)

        if (backwardTransferCertificate.isDefined)
          sidechainHashMap.addCertificate(backwardTransferCertificate.get)

        val sidechainHash = sidechainHashMap.getSidechainHash(sidechainId)

        if (!util.Arrays.equals(header.hashScTxsCommitment, mproof.get.apply(sidechainHash)))
          return false

        if (sidechainRelatedAggregatedTransaction.isDefined &&
          !sidechainRelatedAggregatedTransaction.get.semanticValidity())
          return false

      } else {
        //If we don't have proofOfNoData
        if (proofOfNoData._1.isEmpty && proofOfNoData._2.isEmpty)
          return false

        //Check if there are no sidechains between neighbours
        if (proofOfNoData._1.isDefined && proofOfNoData._2.isDefined &&
          proofOfNoData._2.get.merklePath.leafIndex() - 1 != proofOfNoData._1.get.merklePath.leafIndex())
          return false

        //Check if there is only right neighbour it must be leftmost in sidechain merkle tree
        if (proofOfNoData._1.isEmpty && !proofOfNoData._2.get.merklePath.isLeftmost)
          return false

        //Check if there is only left neighbour it must be rightmost in sidechain merkle tree
        if (proofOfNoData._2.isEmpty &&
            !proofOfNoData._1.get.merklePath.isRightmost(SidechainHashList.getSidechainHash(proofOfNoData._1.get)))
          return false

        proofOfNoData._1 match {
          case Some(proof) =>
            if (!(new ByteArrayWrapper(proof.sidechainId) < sidechainId))
              return false
            val merkleRoot = proof.merklePath.apply(SidechainHashList.getSidechainHash(proof))
            if (!util.Arrays.equals(header.hashScTxsCommitment, merkleRoot))
              return false
          case None =>
        }

        proofOfNoData._2 match {
          case Some(proof) =>
            if (!(new ByteArrayWrapper(proof.sidechainId) > sidechainId))
              return false
            val merkleRoot = proof.merklePath.apply(SidechainHashList.getSidechainHash(proof))
            if (!util.Arrays.equals(header.hashScTxsCommitment, merkleRoot))
              return false
          case None =>
        }
      }
    }

    true
  }
}

object MainchainBlockReference {
  // TO DO: check size
  val MAX_MAINCHAIN_BLOCK_SIZE: Int = 2048 * 1024 //2048K
  val SC_CERT_BLOCK_VERSION = 3

  def create(mainchainBlockBytes: Array[Byte], params: NetworkParams): Try[MainchainBlockReference] = { // TO DO: get sidechainId from some params object
    require(mainchainBlockBytes.length < MAX_MAINCHAIN_BLOCK_SIZE)
    require(params.sidechainId.length == 32)

    val tryBlock: Try[MainchainBlockReference] = parseMainchainBlockBytes(mainchainBlockBytes) match {
      case Success((header, mainchainTxs, certificates)) =>
        // Calculate SCMap and verify it
        var scIds: Set[ByteArrayWrapper] = Set[ByteArrayWrapper]()

        val sidechainId = new ByteArrayWrapper(params.sidechainId)

        val sidechainHashMap = new SidechainsHashMap()

        for (tx <- mainchainTxs)
          scIds = scIds ++ tx.getRelatedSidechains

        var mc2scTransaction: Option[MC2SCAggregatedTransaction] = None
        for (id <- scIds) {
          var sidechainRelatedTransactionsOutputs: java.util.ArrayList[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]] = new java.util.ArrayList()
          for (tx <- mainchainTxs) {
            sidechainRelatedTransactionsOutputs.addAll(getSidechainRelatedTransactionsOutputs(tx, id).asJava)
          }
          sidechainHashMap.addTransactionOutputs(id, sidechainRelatedTransactionsOutputs.asScala)
          if(id == sidechainId)
            mc2scTransaction = Some(MC2SCAggregatedTransaction.create(sidechainRelatedTransactionsOutputs, header.time))
        }

        scIds = scIds ++ certificates.map(c => new ByteArrayWrapper(c.sidechainId))

        certificates.foreach(c => sidechainHashMap.addCertificate(c))

        val certificate = certificates.find(c => util.Arrays.equals(c.sidechainId, params.sidechainId))

        if (scIds.isEmpty)
          Success(new MainchainBlockReference(header, None, None, (None, None), None))
        else {
          if (scIds.contains(sidechainId))
            Success(new MainchainBlockReference(header, mc2scTransaction, sidechainHashMap.getMerklePath(sidechainId), (None, None), certificate))
          else
            Success(new MainchainBlockReference(header, mc2scTransaction, None,
              sidechainHashMap.getNeighbourProofs(sidechainId), certificate))
        }
      case Failure(e) =>
        Failure(e)
    }



    if(tryBlock.isFailure)
      tryBlock
    else {
      if(!tryBlock.get.semanticValidity(params))
        throw new Exception("Mainchain Block bytes were parsed, but lead to semantically invalid data.")
      else
        tryBlock
    }
  }

  private def getSidechainRelatedTransactionsOutputs(tx: MainchainTransaction, sidechainId: ByteArrayWrapper): Seq[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]] = {
    var indexInTx: Int = -1
    tx.getCrosschainOutputs(sidechainId).map(output => {
      indexInTx += 1
      output match {
        case creationOutput: MainchainTxSidechainCreationCrosschainOutput =>
          new SidechainCreation(creationOutput, tx.hash, indexInTx)
        case forwartTransferOutput: MainchainTxForwardTransferCrosschainOutput =>
          new ForwardTransfer(forwartTransferOutput, tx.hash, indexInTx)
        case certifierLockOutput: MainchainTxCertifierLockCrosschainOutput =>
          new CertifierLock(certifierLockOutput, tx.hash, indexInTx)
      }
    })
  }

  // Try to parse Mainchain block and return MainchainHeader, SCMap and MainchainTransactions sequence.
  private def parseMainchainBlockBytes(mainchainBlockBytes: Array[Byte]):
    Try[(MainchainHeader, Seq[MainchainTransaction], Seq[MainchainBackwardTransferCertificate])] = Try {
    var offset: Int = 0

    MainchainHeader.create(mainchainBlockBytes, offset) match {
      case Success(header) =>
        offset += header.mainchainHeaderBytes.length

        val transactionsCount: VarInt = BytesUtils.getVarInt(mainchainBlockBytes, offset)
        offset += transactionsCount.size()

        // parse transactions
        var transactions: Seq[MainchainTransaction] = Seq[MainchainTransaction]()

        while (transactions.size < transactionsCount.value()) {
          val tx: MainchainTransaction = MainchainTransaction.create(mainchainBlockBytes, offset).get
          transactions = transactions :+ tx
          offset += tx.size
        }

        var certificates: Seq[MainchainBackwardTransferCertificate] = Seq[MainchainBackwardTransferCertificate]()

        // Parse certificates only if version is the same as specified and there is bytes to parse.
        if (header.version == SC_CERT_BLOCK_VERSION) {
            val certificatesCount: VarInt = BytesUtils.getVarInt(mainchainBlockBytes, offset)
            offset += certificatesCount.size()

            while (certificates.size < certificatesCount.value()) {
              val c: MainchainBackwardTransferCertificate = MainchainBackwardTransferCertificate.parse(mainchainBlockBytes, offset)
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
  val HASH_BYTES_LENGTH: Int = 32

  override def serialize(obj: MainchainBlockReference, w: Writer): Unit = {
    w.putInt(obj.header.bytes.length)
    w.putBytes(obj.header.bytes)
    obj.sidechainRelatedAggregatedTransaction match {
      case Some(tx) =>
        w.putInt(tx.bytes().length)
        w.putBytes(tx.bytes())
      case _ =>
        w.putInt(0)
    }

    obj.mproof match {
      case Some(mp) =>
        w.putInt(mp.bytes().length)
        w.putBytes(mp.bytes())
      case None =>
        w.putInt(0)
    }

    obj.proofOfNoData._1 match {
      case Some(p) =>
        val pb = NeighbourProofSerializer.toBytes(p)
        w.putInt(pb.length)
        w.putBytes(pb)
      case None =>
        w.putInt(0)
    }

    obj.proofOfNoData._2 match {
      case Some(p) =>
        val pb = NeighbourProofSerializer.toBytes(p)
        w.putInt(pb.length)
        w.putBytes(pb)
      case None =>
        w.putInt(0)
    }

    obj.backwardTransferCertificate match {
      case Some(certificate) =>
        val cb = MainchainBackwardTransferCertificateSerializer.toBytes(certificate)
        w.putInt(cb.length)
        w.putBytes(cb)
      case _ => w.putInt(0)
    }

  }

  override def parse(r: Reader): MainchainBlockReference = {
    if(r.remaining < 4 + MainchainHeader.HEADER_SIZE + 4 + 4)
      throw new IllegalArgumentException("Input data corrupted.")

    val headerSize: Int = r.getInt()
    val header: MainchainHeader = MainchainHeaderSerializer.parseBytes(r.getBytes(headerSize))

    val mc2scAggregatedTransactionSize: Int = r.getInt()

    val mc2scTx: Option[MC2SCAggregatedTransaction] = {
      if (mc2scAggregatedTransactionSize > 0)
        Some(MC2SCAggregatedTransactionSerializer.getSerializer.parseBytes(r.getBytes(mc2scAggregatedTransactionSize)))
      else
        None
    }

    val mproofSize = r.getInt()

    val mproof = {
      if (mproofSize > 0)
        Some(MerklePath.parseBytes(r.getBytes(mproofSize)))
      else
        None
    }

    val leftNeighbourSize = r.getInt()

    val leftNeighbour = {
      if (leftNeighbourSize > 0)
        Some(NeighbourProofSerializer.parseBytes(r.getBytes(leftNeighbourSize)))
      else
        None
    }

    val rightNeighbourSize = r.getInt()

    val rightNeighbour = {
      if (rightNeighbourSize > 0)
        Some(NeighbourProofSerializer.parseBytes(r.getBytes(rightNeighbourSize)))
      else
        None
    }

    val certificateSize = r.getInt()

    val certificate = {
      if (certificateSize > 0)
        Some(MainchainBackwardTransferCertificateSerializer.parseBytes(r.getBytes(certificateSize)))
      else
        None
    }

    new MainchainBlockReference(header, mc2scTx, mproof, (leftNeighbour, rightNeighbour), certificate)
  }
}
