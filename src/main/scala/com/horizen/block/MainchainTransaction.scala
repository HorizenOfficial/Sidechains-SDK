package com.horizen.block

import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.transaction.mainchain.SidechainRelatedMainchainOutput
import com.horizen.utils.{BytesUtils, Utils, VarInt}

class MainchainTransaction(
                          transactionsBytes: Array[Byte],
                          offset: Int
                          ) {
  parse()

  private val PHGR_TX_VERSION: Int = 2
  private val GROTH_TX_VERSION: Int = 0xFFFFFFFD // -3
  private val OUTPUTS_V2_TX_VERSION: Int = 0xFFFFFFFC // -4

  private var _size: Int = 0
  private var _version: Int = 0
//  Bitcoin segwit support
//  private var _marker: Byte = 0
//  private var _flag: Byte = 0
//  private var _useSegwit: Boolean = false
  private var _inputs: Seq[MainchainTxInput] = Seq()
  private var _outputs: Seq[MainchainTxOutput] = Seq()
  private var _lockTime: Int = 0

  lazy val bytes: Array[Byte] = transactionsBytes.slice(offset, offset + _size)

  lazy val hash: Array[Byte] = Utils.doubleSHA256Hash(bytes)

  def size = _size


  private def parse() = {
    var currentOffset: Int = offset

    _version = BytesUtils.getInt(transactionsBytes, currentOffset)
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

      _inputs = _inputs :+ new MainchainTxInput(prevTxHash, prevTxOuputIndex, txScript, sequence)
    }

    // parse outputs
    val outputsNumber: VarInt = BytesUtils.getVarInt(transactionsBytes, currentOffset)
    currentOffset += outputsNumber.size()

    if(_version == OUTPUTS_V2_TX_VERSION) { // parse outputs as MainchainTxOutputsV2
      // TO DO: implement
    } else { // parse as MainchainTxOutputsV1
      for (i <- 1 to outputsNumber.value().intValue()) {
        val value: Long = BytesUtils.getLong(transactionsBytes, currentOffset)
        currentOffset += 8

        val scriptLength: VarInt = BytesUtils.getVarInt(transactionsBytes, currentOffset)
        currentOffset += scriptLength.size()

        val script: Array[Byte] = transactionsBytes.slice(currentOffset, currentOffset + scriptLength.value().intValue())
        currentOffset += scriptLength.value().intValue()

        _outputs = _outputs :+ new MainchainTxOutputV1(value, script)
      }
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

    _lockTime = BytesUtils.getInt(transactionsBytes, currentOffset)
    currentOffset += 4


    // check if it's a transaction with JoinSplits and parse them if need
    // Note: actually joinsplit data is not important for us. So we will just parse it for knowing its size
    if (_version >= PHGR_TX_VERSION || _version == GROTH_TX_VERSION) {
      val joinSplitsNumber: VarInt = BytesUtils.getVarInt(transactionsBytes, currentOffset)
      currentOffset += outputsNumber.size()
      for (i <- 1 to joinSplitsNumber.value().intValue()) {
        currentOffset += 8        // int64_t vpub_old
                       + 8        // int64_t vpub_new
                       + 32       // uint256 anchor
                       + 32 * 2   // std::array<uint256, ZC_NUM_JS_INPUTS> nullifiers, where ZC_NUM_JS_INPUTS = 2
                       + 32 * 2   // std::array<uint256, ZC_NUM_JS_OUTPUTS> commitments, where ZC_NUM_JS_OUTPUTS = 2
                       + 32       // uint256 ephemeralKey
                       + 32       // uint256 randomSeed
                       + 32 * 2  // std::array<uint256, ZC_NUM_JS_INPUTS> macs

        if(_version >= PHGR_TX_VERSION) // parse PHGRProof
          currentOffset += 33 * 7 + 65 // PHGRProof consists of 7 CompressedG1  (33 bytes each) + 1 CompressedG2 (65 bytes)
        else // _version == _version == GROTH_TX_VERSION -> parse GrothProof
          currentOffset += 192         // typedef std::array<unsigned char, GROTH_PROOF_SIZE> GrothProof, where GROTH_PROOF_SIZE = 48 + 96 + 48

        currentOffset += 601 * 2 // std::array<ZCNoteEncryption::Ciphertext, ZC_NUM_JS_OUTPUTS>, where typedef std::array<unsigned char, CLEN> Ciphertext and CLEN = 1 + 8 + 32 + 32 + 512 + 16
      }
      if(joinSplitsNumber.value() > 0) {
        currentOffset += 32 // uint256 joinSplitPubKey;
                       + 64 // typedef boost::array<unsigned char, 64> joinsplit_sig_t
      }
    }

    _size = currentOffset - offset
  }

  def getSidechainRelatedOutputs(sidechainId: Array[Byte]): java.util.List[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]] = {
    // TO DO: parse all outputs, detect
    new java.util.ArrayList[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]]()
  }

  // TO DO: implement later, when structure will be known
  // def getSidechainRelatedFraudReports(sidechainId: Array[Byte]):: java.util.List[FraudReport] = ???
  // def getSidechainRelatedCertificateReferences(sidechainId: Array[Byte]):: java.util.List[CertificateReference] = ???
}


// Note: Witness data is ignored during parsing.
class MainchainTxInput(
                        val prevTxHash: Array[Byte],
                        val prevTxOutIndex: Int,
                        val txScript: Array[Byte],
                        val sequence: Int
                      ) {
}