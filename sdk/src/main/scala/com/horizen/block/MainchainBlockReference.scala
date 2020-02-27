package com.horizen.block

import java.util

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonProperty, JsonView}
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.horizen.box.Box
import com.horizen.params.NetworkParams
import com.horizen.proposition.Proposition
import com.horizen.transaction.mainchain.{CertifierLock, ForwardTransfer, SidechainCreation, SidechainRelatedMainchainOutput}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import com.horizen.serialization.{JsonMerkleRootsSerializer, Views}
import scorex.core.serialization.BytesSerializable
import com.horizen.transaction.{MC2SCAggregatedTransaction, MC2SCAggregatedTransactionSerializer}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, MerkleTree, Utils, VarInt}
import scorex.core.serialization.ScorexSerializer
import scorex.util.serialization.{Reader, Writer}
import com.google.common.primitives.Bytes

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
                    @JsonProperty("merkleRoots")
                    @JsonSerialize(using = classOf[JsonMerkleRootsSerializer])
                    val sidechainsMerkleRootsMap: Option[mutable.Map[ByteArrayWrapper, Array[Byte]]],
                    val backwardTransferCertificate: Seq[MainchainBackwardTransferCertificate]
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

    if (util.Arrays.equals(header.hashSCMerkleRootsMap, params.zeroHashBytes)) {
      // If there is not SC related outputs in MC block, SCMap, and AggTx expected to be not defined.
      if (sidechainsMerkleRootsMap.isDefined || sidechainRelatedAggregatedTransaction.isDefined)
        return false
    }
    else {
      if (sidechainsMerkleRootsMap.isEmpty)
        return false

      // verify SCMap Merkle root hash equals to one in the header.
      val SCSeq = sidechainsMerkleRootsMap.get.toIndexedSeq.sortWith((a, b) => a._1.compareTo(b._1) < 0)
      val merkleTreeLeaves = SCSeq.map(pair => {
        BytesUtils.reverseBytes(Utils.doubleSHA256Hash(
            Bytes.concat(
              BytesUtils.reverseBytes(pair._1.data),
              BytesUtils.reverseBytes(pair._2)
            )
        ))
      }).toList.asJava
      val merkleTree: MerkleTree = MerkleTree.createMerkleTree(merkleTreeLeaves)
      if (!util.Arrays.equals(header.hashSCMerkleRootsMap, merkleTree.rootHash()))
        return false

      val sidechainMerkleRootHash = sidechainsMerkleRootsMap.get.get(new ByteArrayWrapper(params.sidechainId))
      if (sidechainMerkleRootHash.isEmpty) {
        // there is no related outputs for current Sidechain, AggTx expected to be not defined.
        return sidechainRelatedAggregatedTransaction.isEmpty
      } else {
        if (sidechainRelatedAggregatedTransaction.isEmpty)
          return false

        // verify AggTx
        if (!util.Arrays.equals(sidechainMerkleRootHash.get, sidechainRelatedAggregatedTransaction.get.mc2scMerkleRootHash())
          || !sidechainRelatedAggregatedTransaction.get.semanticValidity())
          return false
      }
    }

    true
  }
}


object MainchainBlockReference {
  // TO DO: check size
  val MAX_MAINCHAIN_BLOCK_SIZE: Int = 2048 * 1024 //2048K

  def create(mainchainBlockBytes: Array[Byte], params: NetworkParams): Try[MainchainBlockReference] = { // TO DO: get sidechainId from some params object
    require(mainchainBlockBytes.length < MAX_MAINCHAIN_BLOCK_SIZE)
    require(params.sidechainId.length == 32)

    val tryBlock: Try[MainchainBlockReference] = parseMainchainBlockBytes(mainchainBlockBytes) match {
      case Success((header, mainchainTxs, certificates)) =>
        // Calculate SCMap and verify it
        var scIds: Set[ByteArrayWrapper] = Set[ByteArrayWrapper]()
        for (tx <- mainchainTxs)
          scIds = scIds ++ tx.getRelatedSidechains

        if (scIds.isEmpty)
          Success(new MainchainBlockReference(header, None, None, certificates))
        else {
          var aggregatedTransactionsMap: mutable.Map[ByteArrayWrapper, MC2SCAggregatedTransaction] = mutable.Map[ByteArrayWrapper, MC2SCAggregatedTransaction]()
          for (id <- scIds) {
            var sidechainRelatedTransactionsOutputs: java.util.ArrayList[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]] = new java.util.ArrayList()
            for (tx <- mainchainTxs) {
              sidechainRelatedTransactionsOutputs.addAll(getSidechainRelatedTransactionsOutputs(tx, id).asJava)
            }
            aggregatedTransactionsMap.put(id, MC2SCAggregatedTransaction.create(sidechainRelatedTransactionsOutputs, header.time))
          }

          val SCMap: mutable.Map[ByteArrayWrapper, Array[Byte]] = aggregatedTransactionsMap.map {
            case (k, v) =>
              (k, v.mc2scMerkleRootHash())
          }

          val mc2scTransaction: Option[MC2SCAggregatedTransaction] = aggregatedTransactionsMap.get(new ByteArrayWrapper(params.sidechainId))

          Success(new MainchainBlockReference(header, mc2scTransaction, Option(SCMap), certificates))
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

        while(offset < mainchainBlockBytes.length) {
          val tx: MainchainTransaction = MainchainTransaction.create(mainchainBlockBytes, offset).get
          transactions = transactions :+ tx
          offset += tx.size
        }

        if(transactions.size != transactionsCount.value())
          throw new IllegalArgumentException("Input data corrupted. Actual Tx number parsed %d, expected %d".format(transactions.size, transactionsCount.value()))

        if (offset < mainchainBlockBytes.length) {
          val certificatesCount: VarInt = BytesUtils.getVarInt(mainchainBlockBytes, offset)
          offset += certificatesCount.size()

          var certificates: Seq[MainchainBackwardTransferCertificate] = Seq[MainchainBackwardTransferCertificate]()

          while(offset < mainchainBlockBytes.length) {
            val c: MainchainBackwardTransferCertificate = MainchainBackwardTransferCertificate.parse(mainchainBlockBytes, offset)
            certificates = certificates :+ c
            offset += c.size
          }

          if(certificates.size != certificatesCount.value())
            throw new IllegalArgumentException("Input data corrupted. Actual certificates number parsed %d, expected %d".format(certificates.size, transactionsCount.value()))

          (header, transactions, certificates)
        } else {
          (header, transactions, Seq())
        }


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

    obj.sidechainsMerkleRootsMap match {
      case Some(scmap) =>
        w.putInt(scmap.size * HASH_BYTES_LENGTH * 2)
        scmap.foreach {
          case (k, v) =>
            w.putBytes(k.data)
            w.putBytes(v)
        }
      case _ =>
        w.putInt(0)
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

    val SCMapSize: Int = r.getInt()

    if(SCMapSize != r.remaining)
      throw new IllegalArgumentException("Input data corrupted.")

    val SCMap: Option[mutable.Map[ByteArrayWrapper, Array[Byte]]] = {
      if(SCMapSize > 0) {
        val scmap = mutable.Map[ByteArrayWrapper, Array[Byte]]()
        while(r.remaining > 0) {
          scmap.put(new ByteArrayWrapper(r.getBytes(HASH_BYTES_LENGTH)), r.getBytes(HASH_BYTES_LENGTH))
        }
        Some(scmap)
      }
      else
        None
    }

    new MainchainBlockReference(header, mc2scTx, SCMap, Seq())
  }
}
