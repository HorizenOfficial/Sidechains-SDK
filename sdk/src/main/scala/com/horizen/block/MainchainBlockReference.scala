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
                    /*
                    @JsonProperty("merkleRoots")
                    @JsonSerialize(using = classOf[JsonMerkleRootsSerializer])
                    val sidechainsMerkleRootsMap: Option[mutable.Map[ByteArrayWrapper, Array[Byte]]],
                    */
                    val mproof: Option[MerklePath],
                    val proofOfNoData: (Option[(Array[Byte], MerklePath)], Option[(Array[Byte], MerklePath)]),
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

    if (util.Arrays.equals(header.hashSCMerkleRootsMap, params.zeroHashBytes)) {
      // If there is not SC related outputs in MC block, SCMap, AggTx and Certificate expected to be not defined.
      if (mproof.isDefined ||
          proofOfNoData._1.isDefined ||
          proofOfNoData._2.isDefined ||
          sidechainRelatedAggregatedTransaction.isDefined ||
          backwardTransferCertificate.isDefined)
        return false
    }
    else {

      //Checks if we have proof
      if (mproof.isDefined) {

        //Check for empty transaction and certificate
        if (sidechainRelatedAggregatedTransaction.isEmpty && backwardTransferCertificate.isEmpty)
          return false

        val sidechainId = new ByteArrayWrapper(params.sidechainId)
        val sidechainHashMap = new SidechainHashMap()

        if (sidechainRelatedAggregatedTransaction.isDefined)
          sidechainHashMap.addTransactionOutputs(sidechainId,
            sidechainRelatedAggregatedTransaction.get.mc2scTransactionsOutputs().asScala)

        if (backwardTransferCertificate.isDefined)
          sidechainHashMap.addCertificate(backwardTransferCertificate.get)

        val sidechainMerkleRoot = sidechainHashMap.getMerkleRoot(sidechainId)

        val m0 = BytesUtils.toHexString(sidechainMerkleRoot)
        val m1 = BytesUtils.toHexString(mproof.get.apply(sidechainMerkleRoot))
        val m2 = BytesUtils.toHexString(header.hashSCMerkleRootsMap)

        if (!util.Arrays.equals(header.hashSCMerkleRootsMap, mproof.get.apply(sidechainMerkleRoot)))
          return false

        if (sidechainRelatedAggregatedTransaction.nonEmpty &&
          !sidechainRelatedAggregatedTransaction.get.semanticValidity())
          return false

      } else {
        //If we don't have proofOfNoData
        if (proofOfNoData._1.isEmpty && proofOfNoData._2.isEmpty)
          return false

        proofOfNoData._1 match {
          case Some(proof) =>
            val merkleRoot = proof._2.apply(proof._1)
            if (!util.Arrays.equals(header.hashSCMerkleRootsMap, merkleRoot))
              return false
          case None =>
        }

        proofOfNoData._2 match {
          case Some(proof) =>
            val merkleRoot = proof._2.apply(proof._1)
            if (!util.Arrays.equals(header.hashSCMerkleRootsMap, merkleRoot))
              return false
          case None =>
        }
      }

      /*
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
        // there is no related outputs for current Sidechain, AggTx and Certificate expected to be not defined.
        return sidechainRelatedAggregatedTransaction.isEmpty && backwardTransferCertificate.isEmpty
      } else {
        if (sidechainRelatedAggregatedTransaction.isEmpty && backwardTransferCertificate.isEmpty)
          return false

        val sidechainHashes = SidechainHashes.empty

        sidechainRelatedAggregatedTransaction.foreach(tx =>
          sidechainHashes.addTransactionOutputs(new ByteArrayWrapper(params.sidechainId), tx.mc2scTransactionsOutputs().asScala))

        backwardTransferCertificate.foreach(bt =>
          sidechainHashes.addCertificate(bt))

        // verify AggTx and certificate
        if (!util.Arrays.equals(sidechainMerkleRootHash.get, sidechainHashes.getMerkleRoot(params.sidechainId)))
          return false

        if (sidechainRelatedAggregatedTransaction.nonEmpty &&
          !sidechainRelatedAggregatedTransaction.get.semanticValidity())
          return false
      }
      */
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

        val sidechaiId = new ByteArrayWrapper(params.sidechainId)

        val sidechainHashMap = new SidechainHashMap()

        for (tx <- mainchainTxs)
          scIds = scIds ++ tx.getRelatedSidechains

        val mc2scTransaction: Option[MC2SCAggregatedTransaction] = if (scIds.nonEmpty) {
          var aggregatedTransactionsMap: mutable.Map[ByteArrayWrapper, MC2SCAggregatedTransaction] = mutable.Map[ByteArrayWrapper, MC2SCAggregatedTransaction]()
          for (id <- scIds) {
            var sidechainRelatedTransactionsOutputs: java.util.ArrayList[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]] = new java.util.ArrayList()
            for (tx <- mainchainTxs) {
              sidechainRelatedTransactionsOutputs.addAll(getSidechainRelatedTransactionsOutputs(tx, id).asJava)
            }
            aggregatedTransactionsMap.put(id, MC2SCAggregatedTransaction.create(sidechainRelatedTransactionsOutputs, header.time))
          }

          aggregatedTransactionsMap.foreach( f=>
            sidechainHashMap.addTransactionOutputs(f._1, f._2.mc2scTransactionsOutputs().asScala))

          aggregatedTransactionsMap.get(sidechaiId)

        } else {
          None
        }

        scIds = scIds ++ certificates.map(c => new ByteArrayWrapper(c.sidechainId))

        certificates.foreach(c => sidechainHashMap.addCertificate(c))

        val certificate = certificates.find(c => util.Arrays.equals(c.sidechainId, params.sidechainId))

        if (scIds.isEmpty)
          Success(new MainchainBlockReference(header, None, None, (None, None), None))
        else {
          if (scIds.exists(c => util.Arrays.equals(c.data, params.sidechainId)))
            Success(new MainchainBlockReference(header, mc2scTransaction, sidechainHashMap.getMerklePath(sidechaiId), (None, None), certificate))
          else
            Success(new MainchainBlockReference(header, mc2scTransaction, None,
              sidechainHashMap.getNeighborsMerklePaths(sidechaiId), certificate))
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

    val certificateSize = r.getInt()

    val certificate = {
      if (certificateSize != 0)
        Some(MainchainBackwardTransferCertificateSerializer.parseBytes(r.getBytes(certificateSize)))
      else
        None
    }

    new MainchainBlockReference(header, mc2scTx, None, (None, None), certificate)
  }
}
