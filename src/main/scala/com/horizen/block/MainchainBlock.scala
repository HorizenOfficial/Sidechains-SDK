package com.horizen.block

import java.util

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.box.Box
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
                    val SCMapMerklePath: Option[MerklePath]
                    ) extends BytesSerializable {

  lazy val hash: Array[Byte] = header.hash

  lazy val hashHex: String = BytesUtils.toHexString(hash)

  override type M = MainchainBlock

  override def serializer: Serializer[MainchainBlock] = MainchainBlockSerializer

  def semanticValidity(): Boolean = {
    if(header == null || !header.semanticValidity())
      return false
    if(sidechainRelatedAggregatedTransaction.isDefined != SCMapMerklePath.isDefined)
      return false

    if(sidechainRelatedAggregatedTransaction.isDefined) {
      val rootHash: Array[Byte] = SCMapMerklePath.get.apply(sidechainRelatedAggregatedTransaction.get.mc2scMerkleRootHash())
      if(!util.Arrays.equals(header.hashSCMerkleRootsMap, rootHash))
        return false
    }

    true
  }
}


object MainchainBlock {
  // TO DO: check size
  val MAX_MAINCHAIN_BLOCK_SIZE = 2048 * 1024 //2048K

  def create(mainchainBlockBytes: Array[Byte], sidechainId: Array[Byte]): Try[MainchainBlock] = {
    require(mainchainBlockBytes.length < MAX_MAINCHAIN_BLOCK_SIZE)
    require(sidechainId.length == 32)

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
                sidechainRelatedTransactionsOutputs.addAll(tx.getSidechainRelatedOutputs(sidechainId))
                // TO DO: put Certificate and FraudReports processing later.
              }
              aggregatedTransactionsMap.put(id, MC2SCAggregatedTransaction.create(header.hash, sidechainRelatedTransactionsOutputs, header.time))
            }

            val SCMap: Map[ByteArrayWrapper, Array[Byte]] = aggregatedTransactionsMap.map {
              case (k, v) =>
                (k, v.mc2scMerkleRootHash())
            }

            // verify SCMap
            val SCSeq = SCMap.toIndexedSeq.sortWith((a, b) => a._1.compareTo(b._1) < 0)
            val sidechainsMerkleRootsHashesList = SCSeq.map(_._2).toList.asJava
            val merkleTree: MerkleTree = MerkleTree.createMerkleTree(sidechainsMerkleRootsHashesList)
            if(!util.Arrays.equals(header.hashSCMerkleRootsMap, merkleTree.rootHash()))
              throw new Exception("Mainchain Block MC2SCAggregatedTransactions were parsed, but lead to different SCMerkleRootsMap hash.")


            val mc2scTransaction: Option[MC2SCAggregatedTransaction] = aggregatedTransactionsMap.get(new ByteArrayWrapper(sidechainId))

            // Calculate MerklePath for current Sidechain in SCMap
            val SCMapMerklePath: Option[MerklePath] = mc2scTransaction match {
              case Some(_) =>
                val indexOfSidechain = SCSeq.indexWhere(a => a._1.equals(new ByteArrayWrapper(sidechainId)))
                Some(merkleTree.getMerklePathForLeaf(indexOfSidechain))

              case None => None
            }

            Success(new MainchainBlock(header, mc2scTransaction, SCMapMerklePath))
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
      if(!tryBlock.get.semanticValidity())
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

        val transactionsSize: VarInt = BytesUtils.getVarInt(mainchainBlockBytes, offset)
        offset += transactionsSize.size()

        // parse transactions
        var transactions: Seq[MainchainTransaction] = Seq[MainchainTransaction]()

        while(offset < mainchainBlockBytes.length) {
          val tx: MainchainTransaction = new MainchainTransaction(mainchainBlockBytes, offset)
          transactions = transactions :+ tx
          offset += tx.size
        }

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

    val merklePathSize: Int = obj.SCMapMerklePath match {
      case Some(path) => path.bytes.length
      case _ => 0
    }

    Bytes.concat(
      Ints.toByteArray(obj.header.bytes.length),
      obj.header.bytes,
      Ints.toByteArray(mc2scAggregatedTransactionSize),
      if (mc2scAggregatedTransactionSize == 0) Array[Byte]() else obj.sidechainRelatedAggregatedTransaction.get.bytes,
      Ints.toByteArray(merklePathSize),
      if (merklePathSize == 0) Array[Byte]() else obj.SCMapMerklePath.get.bytes
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

    val merklePathSize: Int = BytesUtils.getInt(bytes, offset)
    offset += 4

    val SCMapMerklePath: Option[MerklePath] = {
      if (merklePathSize > 0)
        Some(MerklePath.parseBytes(bytes.slice(offset, offset + merklePathSize)).get)
      else
        None
    }

    new MainchainBlock(header, mc2scTx, SCMapMerklePath)
  }
}