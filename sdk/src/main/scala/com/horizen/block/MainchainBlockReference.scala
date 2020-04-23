package com.horizen.block

import java.util
import java.util.List

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
import com.horizen.validation.{InconsistentMainchainBlockReferenceDataException, InvalidMainchainDataException}
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

  override type M = MainchainBlockReference

  override def serializer: ScorexSerializer[MainchainBlockReference] = MainchainBlockReferenceSerializer

  def semanticValidity(params: NetworkParams): Try[Unit] = Try {
    // Check that header is valid.
    header.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => throw e
    }

    // Check that header hash and data hash are the same.
    if(!data.headerHash.sameElements(header.hash))
      throw new InvalidMainchainDataException("MainchainBlockReferenceData header hash and MainchainHeader hash are different.")

    // Check Data consistency to Data header hash.
    if (util.Arrays.equals(header.hashSCMerkleRootsMap, params.zeroHashBytes)) {
      // If there is not SC related outputs in MC block, SCMap, and AggTx expected to be not defined.
      if (data.sidechainsMerkleRootsMap.isDefined || data.sidechainRelatedAggregatedTransaction.isDefined)
        throw new InconsistentMainchainBlockReferenceDataException("MainchainBlockReferenceData is inconsistent to MainchainHeader")
    }
    else {
      val sidechainsMerkleRootsMap = data.sidechainsMerkleRootsMap.getOrElse(
        throw new InconsistentMainchainBlockReferenceDataException("MainchainBlockReferenceData SCMap is inconsistent to MainchainHeader hashSCMerkleRootsMap."))

      // verify SCMap Merkle root hash equals to one in the header.
      val SCSeq = sidechainsMerkleRootsMap.toIndexedSeq.sortWith((a, b) => a._1.compareTo(b._1) < 0)
      val merkleTreeLeaves = SCSeq.map(pair => {
        BytesUtils.reverseBytes(Utils.doubleSHA256Hash(
            Bytes.concat(
              BytesUtils.reverseBytes(pair._1.data),
              BytesUtils.reverseBytes(pair._2)
            )
        ))
      }).toList.asJava
      val merkleTree: MerkleTree = MerkleTree.createMerkleTree(merkleTreeLeaves)
      if(merkleTree.isMutated)
        throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} SCMap leads to mutated MerkleTree.")
      if (!util.Arrays.equals(header.hashSCMerkleRootsMap, merkleTree.rootHash()))
        throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} SCMap root hash is inconsistent to MainchainHeader hashSCMerkleRootsMap.")

      val sidechainMerkleRootHash = sidechainsMerkleRootsMap.get(new ByteArrayWrapper(params.sidechainId))
      if (sidechainMerkleRootHash.isEmpty) {
        // there is no related outputs for current Sidechain, AggTx expected to be not defined.
        if(data.sidechainRelatedAggregatedTransaction.nonEmpty)
          throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} AggTx is inconsistent to sidechain MerkleRootHash.")
      } else {
        val sidechainRelatedAggregatedTransaction = data.sidechainRelatedAggregatedTransaction.getOrElse(
          throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} AggTx is inconsistent to sidechain MerkleRootHash."))


        // verify AggTx
        val mc2scTransactionsOutputsMerkleTree = MerkleTree.createMerkleTree(
          sidechainRelatedAggregatedTransaction.mc2scTransactionsOutputs().asScala.map(_.hash()).asJava)

        if(mc2scTransactionsOutputsMerkleTree.isMutated)
          throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} AggTx outputs leads to mutated MerkleTree.")
        if(!util.Arrays.equals(sidechainMerkleRootHash.get, mc2scTransactionsOutputsMerkleTree.rootHash()))
          throw new InconsistentMainchainBlockReferenceDataException(s"MainchainBlockReferenceData ${header.hashHex} AggTx is inconsistent to sidechain MerkleRootHash.")

        if (!sidechainRelatedAggregatedTransaction.semanticValidity())
          throw new InvalidMainchainDataException(s"MainchainBlockReferenceData ${header.hashHex} AggTx is semantically invalid.")
      }
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
            aggregatedTransactionsMap.put(id, new MC2SCAggregatedTransaction(sidechainRelatedTransactionsOutputs, header.time))
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
      tryBlock.get.semanticValidity(params).get
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
