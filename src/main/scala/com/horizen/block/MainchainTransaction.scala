package com.horizen.block

import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.transaction.mainchain.SidechainRelatedMainchainOutput
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, Utils, VarInt}

import scala.collection.mutable.ArrayBuffer
import java.util.{List => JList, ArrayList => JArrayList}

class MainchainTransaction(
                          transactionsBytes: Array[Byte],
                          offset: Int
                          ) {

  private val PHGR_TX_VERSION: Int = 2
  private val GROTH_TX_VERSION: Int = 0xFFFFFFFD // -3
  private val CROSSCHAIN_OUTPUTS_TX_VERSION: Int = 0xFFFFFFFC // -4

  private var _size: Int = 0
  private var _version: Int = 0
//  Bitcoin segwit support
//  private var _marker: Byte = 0
//  private var _flag: Byte = 0
//  private var _useSegwit: Boolean = false
  private var _inputs: ArrayBuffer[MainchainTxInput] = ArrayBuffer()
  private var _outputs: ArrayBuffer[MainchainTxOutput] = ArrayBuffer()
  private var _crosschainOutputsMap: Map[ByteArrayWrapper, JArrayList[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]]] = Map() // key sidechainID
  private var _lockTime: Int = 0

  parse()

  lazy val bytes: Array[Byte] = transactionsBytes.slice(offset, offset + _size)

  lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(bytes))

  lazy val hashHex: String = BytesUtils.toHexString(hash)

  def size = _size


  private def parse() = {
    var currentOffset: Int = offset

    _version = BytesUtils.getReversedInt(transactionsBytes, currentOffset)
    currentOffset += 4

//    Bitcoin segwit support
//    _marker = transactionsBytes(currentOffset)
//    currentOffset += 1
//
//    _flag = transactionsBytes(currentOffset)
//    currentOffset += 1

    // parse inputs
    val inputsNumber: VarInt = BytesUtils.getVarInt(transactionsBytes, currentOffset)
    currentOffset += inputsNumber.size()

    for (i <- 1 to inputsNumber.value().intValue()) {
      val prevTxHash: Array[Byte] = transactionsBytes.slice(currentOffset, currentOffset + 32)
      currentOffset += 32

      val prevTxOuputIndex: Int = BytesUtils.getInt(transactionsBytes, currentOffset)
      currentOffset += 4

      val scriptLength: VarInt = BytesUtils.getVarInt(transactionsBytes, currentOffset)
      currentOffset += scriptLength.size()

      val txScript: Array[Byte] = transactionsBytes.slice(currentOffset, currentOffset + scriptLength.value().intValue())
      currentOffset += scriptLength.value().intValue()

      val sequence: Int = BytesUtils.getInt(transactionsBytes, currentOffset)
      currentOffset += 4

      _inputs += new MainchainTxInput(prevTxHash, prevTxOuputIndex, txScript, sequence)
    }

    // parse outputs
    val outputsNumber: VarInt = BytesUtils.getVarInt(transactionsBytes, currentOffset)
    currentOffset += outputsNumber.size()

    for (i <- 1 to outputsNumber.value().intValue()) {
      val value: Long = BytesUtils.getReversedLong(transactionsBytes, currentOffset)
      currentOffset += 8

      val scriptLength: VarInt = BytesUtils.getVarInt(transactionsBytes, currentOffset)
      currentOffset += scriptLength.size()

      val script: Array[Byte] = transactionsBytes.slice(currentOffset, currentOffset + scriptLength.value().intValue())
      val scriptHex: String = BytesUtils.toHexString(script)
      currentOffset += scriptLength.value().intValue()

      _outputs = _outputs :+ MainchainTxOutput(value, script)
    }

    if(_version == CROSSCHAIN_OUTPUTS_TX_VERSION) {
      // parse crosschain outputs
      val crosschainOutputsNumber: VarInt = BytesUtils.getVarInt(transactionsBytes, currentOffset)
      currentOffset += crosschainOutputsNumber.size()
      // TO DO: implement parse into _crosschainOutputs

    }

//    if(_marker == 0)
//      _useSegwit = true
//
//    // parse witness
//    // Note: actually witness data is not important for us. So we will just parse it for knowing its size.
//    if(_useSegwit) {
//      val witnessNumber: Long = inputsNumber.value()
//      for (i <- 1 to witnessNumber.intValue()) {
//        val pushCount: VarInt = BytesUtils.getVarInt(transactionsBytes, currentOffset)
//        currentOffset += pushCount.size()
//        for( j <- 1 to pushCount.value().intValue()) {
//          val pushSize: VarInt = BytesUtils.getVarInt(transactionsBytes, currentOffset)
//          currentOffset += pushSize.size() + pushSize.value().intValue()
//        }
//      }
//    }

    // TO DO: check. maybe need to use getInt
    _lockTime = BytesUtils.getReversedInt(transactionsBytes, currentOffset)
    if(_lockTime != 0)
      _lockTime + 4
    currentOffset += 4


    // check if it's a transaction with JoinSplits and parse them if need
    // Note: actually joinsplit data is not important for us. So we will just parse it for knowing its size
    if (_version >= PHGR_TX_VERSION || _version == GROTH_TX_VERSION) {
      val joinSplitsNumber: VarInt = BytesUtils.getVarInt(transactionsBytes, currentOffset)
      currentOffset += joinSplitsNumber.size()

      var joinSplitsOffset: Int = 0
      joinSplitsOffset += 8        // int64_t vpub_old
                       + 8        // int64_t vpub_new
                       + 32       // uint256 anchor
                       + 32 * 2   // std::array<uint256, ZC_NUM_JS_INPUTS> nullifiers, where ZC_NUM_JS_INPUTS = 2
                       + 32 * 2   // std::array<uint256, ZC_NUM_JS_OUTPUTS> commitments, where ZC_NUM_JS_OUTPUTS = 2
                       + 32       // uint256 ephemeralKey
                       + 32       // uint256 randomSeed
                       + 32 * 2  // std::array<uint256, ZC_NUM_JS_INPUTS> macs

      if(_version >= PHGR_TX_VERSION) // parse PHGRProof
        joinSplitsOffset += 33 * 7 + 65 // PHGRProof consists of 7 CompressedG1  (33 bytes each) + 1 CompressedG2 (65 bytes)
      else // _version == _version == GROTH_TX_VERSION -> parse GrothProof
        joinSplitsOffset += 192         // typedef std::array<unsigned char, GROTH_PROOF_SIZE> GrothProof, where GROTH_PROOF_SIZE = 48 + 96 + 48

      joinSplitsOffset += 601 * 2 // std::array<ZCNoteEncryption::Ciphertext, ZC_NUM_JS_OUTPUTS>, where typedef std::array<unsigned char, CLEN> Ciphertext and CLEN = 1 + 8 + 32 + 32 + 512 + 16
      joinSplitsOffset += 360 // TO DO: check from where these 360 byte are taken

      joinSplitsOffset *= joinSplitsNumber.value().intValue()

      if(joinSplitsNumber.value() > 0) {
        joinSplitsOffset += 32 // uint256 joinSplitPubKey;
                       + 64 // typedef boost::array<unsigned char, 64> joinsplit_sig_t
      }

      currentOffset += joinSplitsOffset
    }

    _size = currentOffset - offset
  }

  def getRelatedSidechains: Set[ByteArrayWrapper] = {
    _crosschainOutputsMap.keySet
  }

  def getSidechainRelatedOutputs(sidechainId: ByteArrayWrapper): JList[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]] = {
    _crosschainOutputsMap.getOrElse(sidechainId, new JArrayList[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]]())
  }

  // TO DO: implement later, when structure will be known
  // def getSidechainRelatedFraudReports(sidechainId: Array[Byte]):: java.util.List[FraudReport] = ???
  // def getSidechainRelatedCertificateReferences(sidechainId: Array[Byte]):: java.util.List[CertificateReference] = ???
}


// Note: Witness data is ignored during parsing.
case class MainchainTxInput(
                        prevTxHash: Array[Byte],
                        prevTxOutIndex: Int,
                        txScript: Array[Byte],
                        sequence: Int
                      )