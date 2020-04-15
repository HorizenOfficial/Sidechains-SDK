package com.horizen.block

import java.util

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonProperty, JsonView}
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.horizen.box.Box
import com.horizen.params.NetworkParams
import com.horizen.proposition.Proposition
import com.horizen.transaction.mainchain.{CertifierLock, ForwardTransfer, SidechainCreation, SidechainRelatedMainchainOutput}
import com.horizen.serialization.{JsonMerkleRootsSerializer, Views}
import scorex.core.serialization.BytesSerializable
import com.horizen.transaction.{MC2SCAggregatedTransaction, MC2SCAggregatedTransactionSerializer}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, MerkleTree, Utils, VarInt}
import scorex.core.serialization.ScorexSerializer
import scorex.util.serialization.{Reader, Writer}
import com.google.common.primitives.Bytes
import scorex.crypto.hash.Blake2b256

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
case class MainchainBlockReference(
                    header: MainchainHeader,
                    data: MainchainBlockReferenceData
                    )
  extends BytesSerializable
{

  // MainchainBlockReference Id must depend on the whole MainchainBlockReference content
  // Note: In future inside snarks id calculation will be different
  lazy val id: Array[Byte] = {
    Blake2b256(Bytes.concat(
      header.hash,
      data.hash
    ))
  }

  override type M = MainchainBlockReference

  override def serializer: ScorexSerializer[MainchainBlockReference] = MainchainBlockReferenceSerializer

  // TODO: change return type to Try[Unit]
  def semanticValidity(params: NetworkParams): Boolean = {
    if (header == null || header.semanticValidity(params).isFailure)
      return false

    if(data == null || !data.headerHash.sameElements(header.hash))
      return false

    if (util.Arrays.equals(header.hashSCMerkleRootsMap, params.zeroHashBytes)) {
      // If there is not SC related outputs in MC block, SCMap, and AggTx expected to be not defined.
      if (data.sidechainsMerkleRootsMap.isDefined || data.sidechainRelatedAggregatedTransaction.isDefined)
        return false
    }
    else {
      if (data.sidechainsMerkleRootsMap.isEmpty)
        return false

      // verify SCMap Merkle root hash equals to one in the header.
      val SCSeq = data.sidechainsMerkleRootsMap.get.toIndexedSeq.sortWith((a, b) => a._1.compareTo(b._1) < 0)
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

      val sidechainMerkleRootHash = data.sidechainsMerkleRootsMap.get.get(new ByteArrayWrapper(params.sidechainId))
      if (sidechainMerkleRootHash.isEmpty) {
        // there is no related outputs for current Sidechain, AggTx expected to be not defined.
        return data.sidechainRelatedAggregatedTransaction.isEmpty
      } else {
        if (data.sidechainRelatedAggregatedTransaction.isEmpty)
          return false

        // verify AggTx
        if (!util.Arrays.equals(sidechainMerkleRootHash.get, data.sidechainRelatedAggregatedTransaction.get.mc2scMerkleRootHash())
          || !data.sidechainRelatedAggregatedTransaction.get.semanticValidity())
          return false
      }
    }

    true
  }

  override def hashCode(): Int = java.util.Arrays.hashCode(id)

  override def equals(obj: Any): Boolean = {
    obj match {
      case ref: MainchainBlockReference => id.sameElements(ref.id)
      case _ => false
    }
  }
}


object MainchainBlockReference {
  // TO DO: check size
  val MAX_MAINCHAIN_BLOCK_SIZE: Int = 2048 * 1024 //2048K

  def create(mainchainBlockBytes: Array[Byte], params: NetworkParams): Try[MainchainBlockReference] = { // TO DO: get sidechainId from some params object
    require(mainchainBlockBytes.length < MAX_MAINCHAIN_BLOCK_SIZE)
    require(params.sidechainId.length == 32)

    val tryBlock: Try[MainchainBlockReference] = parseMainchainBlockBytes(mainchainBlockBytes) match {
      case Success((header, mainchainTxs)) =>
        // Calculate SCMap and verify it
        var scIds: Set[ByteArrayWrapper] = Set[ByteArrayWrapper]()
        for (tx <- mainchainTxs)
          scIds = scIds ++ tx.getRelatedSidechains

        if (scIds.isEmpty)
          Success(MainchainBlockReference(header, MainchainBlockReferenceData(header.hash,None, None)))
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

          Success(MainchainBlockReference(header, MainchainBlockReferenceData(header.hash, mc2scTransaction, Option(SCMap))))
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
  private def parseMainchainBlockBytes(mainchainBlockBytes: Array[Byte]): Try[(MainchainHeader, Seq[MainchainTransaction])] = Try {
    var offset: Int = 0

    MainchainHeader.create(mainchainBlockBytes, offset) match {
      case Success(header) =>
        offset += header.mainchainHeaderBytes.length

        val transactionsCount: VarInt = BytesUtils.getReversedVarInt(mainchainBlockBytes, offset)
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

        (header, transactions)
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
