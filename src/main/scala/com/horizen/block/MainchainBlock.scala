package com.horizen.block

import java.util

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.transaction.{MC2SCAggregatedTransaction, MC2SCAggregatedTransactionSerializer}
import com.horizen.transaction.mainchain.{CertifierLock, ForwardTransfer, SidechainRelatedMainchainOutput}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, VarInt}
import scorex.core.serialization.{BytesSerializable, Serializer}

import scala.util.{Failure, Success, Try}
import scala.collection.mutable.Map

// Mainchain Block structure:
//
// Field                Description                                             Size
// Magic no             value always 0xD9B4BEF9                                 4 bytes
// Blocksize            number of bytes following up to end of block            4 bytes
// Blockheader          consists of 7 items (see @MainchainHeader)              80+32 bytes
// Sidechain counter    positive integer (number of SC mentioned in block)      1-9 bytes
// SCMap                map with merkle roots for each Sidechain transactions   <Sidechain counter> * (32 + 32)
// Transaction counter  positive integer (number of transactions in block)      1-9 bytes
// Transactions         the (non empty) list of transactions                    depends on <Transaction counter>

class MainchainBlock(
                    val header: MainchainHeader,
                    val sidechainRelatedAggregatedTransaction: Option[MC2SCAggregatedTransaction]
                    ) extends BytesSerializable {

  def semanticValidity(): Boolean = ???

  override type M = MainchainBlock

  override def serializer: Serializer[MainchainBlock] = MainchainBlockSerializer
}


object MainchainBlock {
  // TO DO: check size
  val MAX_MAINCHAIN_BLOCK_SIZE = 2048 * 1024 //2048K

  def create(mainchainBlockBytes: Array[Byte], sidechainId: Array[Byte]): Try[MainchainBlock] = {
    require(mainchainBlockBytes.length < MAX_MAINCHAIN_BLOCK_SIZE)
    require(sidechainId.length == 32)

    parseMainchainBlockBytes(mainchainBlockBytes) match {
      case Success((header, scmap, mainchainTxs)) =>
        val mc2scTransaction: Option[MC2SCAggregatedTransaction] = {
          if (scmap.contains(new ByteArrayWrapper(sidechainId))) {
            // get SidechainRelatedMainchainOutput, then create MC2SCAggregatedTransaction
            var sidechainRelatedTransactions: java.util.ArrayList[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]] = new java.util.ArrayList()
            for(tx <- mainchainTxs) {
              sidechainRelatedTransactions.addAll(tx.getSidechainRelatedOutputs(sidechainId))
              // TO DO: put Certificate and FraudReports processing later.
            }
            Some(MC2SCAggregatedTransaction.create(header.hash(), scmap.get(new ByteArrayWrapper(sidechainId)).get, sidechainRelatedTransactions, header.time))
          }
          else
            Option(null)
        }

        // TO DO: we also need to put SCMap or at least Merkle Tree path for current Sidechain mc2scTransaction
        val block = new MainchainBlock(header, mc2scTransaction)
        if(!block.semanticValidity())
          Failure(new Exception("Mainchain Block bytes were parsed, but lead to semantically invalid data."))
        else
          Success(block)
      case Failure(e) =>
        Failure(e)
    }
  }

  // Try to parse Mainchain block and return MainchainHeader, SCMap and MainchainTransactions sequence.
  private def parseMainchainBlockBytes(mainchainBlockBytes: Array[Byte]): Try[(MainchainHeader, Map[ByteArrayWrapper, Array[Byte]], Seq[MainchainTransaction])] = Try {
    var offset: Int = 0

    val magicNumber: Array[Byte] = mainchainBlockBytes.slice(offset, offset + 4)
    offset += 4
    if(!BytesUtils.toHexString(magicNumber).equals("d9b4bef9"))
      throw new IllegalArgumentException("Input data corrupted. Magic number is different")

    val blockSize: Int = BytesUtils.getInt(mainchainBlockBytes, offset)
    offset += 4
    if(blockSize != magicNumber.length - offset)
      throw new IllegalArgumentException("Input data corrupted. Actual block size different to decalred one.")

    MainchainHeader.create(mainchainBlockBytes.slice(offset, offset + MainchainHeader.HEADER_SIZE)) match {
      case Success(header) =>
        offset += MainchainHeader.HEADER_SIZE

        val SCMapItemsSize: VarInt = BytesUtils.getVarInt(mainchainBlockBytes, offset);
        offset += SCMapItemsSize.size();

        // parse SCMap
        val SCMap: Map[ByteArrayWrapper, Array[Byte]] = Map[ByteArrayWrapper, Array[Byte]]()
        val SCMapStartingOffset = offset
        while(offset != SCMapStartingOffset + SCMapItemsSize.value() * 64) {
          SCMap.put(
            new ByteArrayWrapper(mainchainBlockBytes.slice(offset, offset + 32)),
            mainchainBlockBytes.slice(offset + 32, offset + 64)
          )
          offset += 64
        }

        val transactionsSize: VarInt = BytesUtils.getVarInt(mainchainBlockBytes, offset);
        offset += transactionsSize.size()

        // parse transactions
        var transactions: Seq[MainchainTransaction] = Seq[MainchainTransaction]()

        while(offset != mainchainBlockBytes.length) {
          val tx: MainchainTransaction = new MainchainTransaction(mainchainBlockBytes, offset)
          transactions = transactions :+ tx
          offset += tx.size
        }

        (header, SCMap, transactions)
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

    Bytes.concat(
      Ints.toByteArray(MainchainHeader.HEADER_SIZE), // Stored only for supporting Header version updates
      obj.header.bytes,
      Ints.toByteArray(mc2scAggregatedTransactionSize),
      if (mc2scAggregatedTransactionSize == 0) Array[Byte]() else obj.sidechainRelatedAggregatedTransaction.get.bytes()
    )
  }

  override def parseBytes(bytes: Array[Byte]): Try[MainchainBlock] = Try {
    if(bytes.length < 4 + MainchainHeader.HEADER_SIZE + 4)
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
        Some(MC2SCAggregatedTransactionSerializer.getSerializer.parseBytes(bytes.slice(offset, mc2scAggregatedTransactionSize + offset)).get)
      else
        Option(null)
    }

    new MainchainBlock(header, mc2scTx)
  }
}