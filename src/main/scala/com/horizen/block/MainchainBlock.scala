package com.horizen.block

import java.io.ByteArrayOutputStream
import java.util

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.box.Box
import com.horizen.params.NetworkParams
import com.horizen.proposition.Proposition
import com.horizen.transaction.{MC2SCAggregatedTransaction, MC2SCAggregatedTransactionSerializer}
import com.horizen.transaction.mainchain.SidechainRelatedMainchainOutput
import com.horizen.utils._
import scorex.core.serialization.{BytesSerializable, Serializer}

import scala.util.{Failure, Success, Try}
import scala.collection.mutable.Map
import scala.collection.JavaConverters._

// Mainchain Block structure:
//
// Field                Description                                             Size
// Blockheader          consists of 9 items (see @MainchainHeader)              1487+32 bytes
// Transaction counter  positive integer (number of transactions in block)      1-9 bytes
// Transactions         the (non empty) list of transactions                    depends on <Transaction counter>

class MainchainBlock(
                    val header: MainchainHeader,
                    val sidechainRelatedAggregatedTransaction: Option[MC2SCAggregatedTransaction],
                    val SCMap: Option[Map[ByteArrayWrapper, Array[Byte]]]
                    ) extends BytesSerializable {

  lazy val hash: Array[Byte] = header.hash

  lazy val hashHex: String = BytesUtils.toHexString(hash)

  override type M = MainchainBlock

  override def serializer: Serializer[MainchainBlock] = MainchainBlockSerializer

  def semanticValidity(params: NetworkParams): Boolean = {
    if(header == null || !header.semanticValidity(params))
      return false

    // check Block version
    if(header.version == MainchainHeader.SCMAP_BLOCK_VERSION) {
      if (util.Arrays.equals(header.hashSCMerkleRootsMap, params.zeroHashBytes)) {
        // If there is not SC related outputs in MC block, SCMap, and AggTx expected to be not defined.
        if (SCMap.isDefined || sidechainRelatedAggregatedTransaction.isDefined)
          return false
      }
      else {
        if (SCMap.isEmpty)
          return false

        // verify SCMap Merkle root hash equals to one in the header.
        val SCSeq = SCMap.get.toIndexedSeq.sortWith((a, b) => a._1.compareTo(b._1) < 0)
        val sidechainsMerkleRootsHashesList = SCSeq.map(_._2).toList.asJava
        val merkleTree: MerkleTree = MerkleTree.createMerkleTree(sidechainsMerkleRootsHashesList)
        if (!util.Arrays.equals(header.hashSCMerkleRootsMap, merkleTree.rootHash()))
          return false

        val sidechainMerkleRootHash = SCMap.get.get(new ByteArrayWrapper(params.sidechainId))
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
    } else {
      // Old Block version has no SCMap and AggTx, also Header.hashSCMerkleRootsMap bytes should be zero
      if(!util.Arrays.equals(header.hashSCMerkleRootsMap, params.zeroHashBytes)
          || SCMap.isDefined || sidechainRelatedAggregatedTransaction.isDefined)
        return false
    }

    true
  }
}


object MainchainBlock {
  // TO DO: check size
  val MAX_MAINCHAIN_BLOCK_SIZE = 2048 * 1024 //2048K

  def create(mainchainBlockBytes: Array[Byte], params: NetworkParams): Try[MainchainBlock] = { // TO DO: get sidechainId from some params object
    require(mainchainBlockBytes.length < MAX_MAINCHAIN_BLOCK_SIZE)
    require(params.sidechainId.length == 32)

    val tryBlock: Try[MainchainBlock] = parseMainchainBlockBytes(mainchainBlockBytes) match {
      case Success((header, mainchainTxs)) =>
        header.version match {
          case MainchainHeader.SCMAP_BLOCK_VERSION => {
            // Calculate SCMap and verify it
            var scIds: Set[ByteArrayWrapper] = Set[ByteArrayWrapper]()
            for (tx <- mainchainTxs)
              scIds = scIds ++ tx.getRelatedSidechains()

            var aggregatedTransactionsMap: Map[ByteArrayWrapper, MC2SCAggregatedTransaction] = Map[ByteArrayWrapper, MC2SCAggregatedTransaction]()
            for (id <- scIds) {
              var sidechainRelatedTransactionsOutputs: java.util.ArrayList[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]] = new java.util.ArrayList()
              for (tx <- mainchainTxs) {
                sidechainRelatedTransactionsOutputs.addAll(tx.getSidechainRelatedOutputs(params.sidechainId))
                // TO DO: put Certificate and FraudReports processing later.
              }
              aggregatedTransactionsMap.put(id, MC2SCAggregatedTransaction.create(sidechainRelatedTransactionsOutputs, header.time))
            }

            val SCMap: Map[ByteArrayWrapper, Array[Byte]] = aggregatedTransactionsMap.map {
              case (k, v) =>
                (k, v.mc2scMerkleRootHash())
            }

            val mc2scTransaction: Option[MC2SCAggregatedTransaction] = aggregatedTransactionsMap.get(new ByteArrayWrapper(params.sidechainId))

            Success(new MainchainBlock(header, mc2scTransaction, Option(SCMap)))
          }

          case _ =>
            Success(new MainchainBlock(header, None, None))

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

  // Try to parse Mainchain block and return MainchainHeader, SCMap and MainchainTransactions sequence.
  private def parseMainchainBlockBytes(mainchainBlockBytes: Array[Byte]): Try[(MainchainHeader, Seq[MainchainTransaction])] = Try {
    var offset: Int = 0

    MainchainHeader.create(mainchainBlockBytes, offset) match {
      case Success(header) =>
        offset += header.mainchainHeaderBytes.length

        val transactionsCount: VarInt = BytesUtils.getVarInt(mainchainBlockBytes, offset)
        offset += transactionsCount.size()

        // parse transactions
        var transactions: Seq[MainchainTransaction] = Seq[MainchainTransaction]()

        while(offset < mainchainBlockBytes.length) {
          val tx: MainchainTransaction = new MainchainTransaction(mainchainBlockBytes, offset)
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



object MainchainBlockSerializer extends Serializer[MainchainBlock] {
  override def toBytes(obj: MainchainBlock): Array[Byte] = {
    val mc2scAggregatedTransactionSize: Int = obj.sidechainRelatedAggregatedTransaction match {
      case Some(tx) => tx.bytes().length
      case _ => 0
    }

    val SCMapSize: Int = obj.SCMap match {
      case Some(scmap) => scmap.size * (32 + 32)
      case _ => 0
    }
    val SCMapBytes: Array[Byte] = {
      if(SCMapSize == 0)
        Array[Byte]()
      else {
        val stream: ByteArrayOutputStream = new ByteArrayOutputStream()
        obj.SCMap.get.foreach {
          case (k, v) => {
            stream.write(k.data())
            stream.write(v)
          }
        }
        stream.toByteArray
      }
    }

    Bytes.concat(
      Ints.toByteArray(obj.header.bytes.length),
      obj.header.bytes,
      Ints.toByteArray(mc2scAggregatedTransactionSize),
      if (mc2scAggregatedTransactionSize == 0) Array[Byte]() else obj.sidechainRelatedAggregatedTransaction.get.bytes,
      Ints.toByteArray(SCMapSize),
      SCMapBytes
    )
  }

  override def parseBytes(bytes: Array[Byte]): Try[MainchainBlock] = Try {
    if(bytes.length < 4 + MainchainHeader.MIN_HEADER_SIZE + 4 + 4)
      throw new IllegalArgumentException("Input data corrupted.")

    var offset: Int = 0
    val headerSize: Int = BytesUtils.getInt(bytes, offset)
    offset += 4

    val header: MainchainHeader = MainchainHeaderSerializer.parseBytes(bytes.slice(offset, headerSize + offset)).get
    offset += headerSize

    val mc2scAggregatedTransactionSize: Int = BytesUtils.getInt(bytes, offset)
    offset += 4

    val mc2scTx: Option[MC2SCAggregatedTransaction] = {
      if (mc2scAggregatedTransactionSize > 0)
        Some(MC2SCAggregatedTransactionSerializer.getSerializer.parseBytes(bytes.slice(offset, offset + mc2scAggregatedTransactionSize)).get)
      else
        None
    }
    offset += mc2scAggregatedTransactionSize

    val SCMapSize: Int = BytesUtils.getInt(bytes, offset)
    offset += 4

    if(offset + SCMapSize != bytes.length)
      throw new IllegalArgumentException("Input data corrupted.")

    val SCMap: Option[Map[ByteArrayWrapper, Array[Byte]]] = {
      if(SCMapSize > 0) {
        val scmap = Map[ByteArrayWrapper, Array[Byte]]()
        while(offset < bytes.length) {
          scmap.put(new ByteArrayWrapper(bytes.slice(offset, offset + 32)), bytes.slice(offset + 32, offset + 64))
          offset += 64
        }
        Some(scmap)
      }
      else
        None
    }

    new MainchainBlock(header, mc2scTx, SCMap)
  }
}