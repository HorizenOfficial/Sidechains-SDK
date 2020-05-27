package com.horizen.block

import com.horizen.utils.{ByteArrayWrapper, BytesUtils, Utils, VarInt}
import scala.util.Try


class MainchainTransaction(
                          transactionsBytes: Array[Byte],
                          version: Int,
                          crosschainOutputsMap: Map[ByteArrayWrapper, Seq[MainchainTxCrosschainOutput]]
                          ) {

  lazy val bytes: Array[Byte] = transactionsBytes.clone()

  lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(transactionsBytes))

  lazy val hashHex: String = BytesUtils.toHexString(hash)

  def size: Int = transactionsBytes.length

  def getRelatedSidechains: Set[ByteArrayWrapper] = crosschainOutputsMap.keySet

  def getCrosschainOutputs(sidechainId: ByteArrayWrapper): Seq[MainchainTxCrosschainOutput] = crosschainOutputsMap.getOrElse(sidechainId, Seq())
}


object MainchainTransaction {
  private val PHGR_TX_VERSION: Int = 2
  private val GROTH_TX_VERSION: Int = 0xFFFFFFFD // -3
  private val SC_TX_VERSION: Int = 0xFFFFFFFC // -4

  def create(transactionBytes: Array[Byte], offset: Int): Try[MainchainTransaction] = Try {
    var crosschainOutputsMap: Map[ByteArrayWrapper, Seq[MainchainTxCrosschainOutput]] = Map() // key sidechainID
    var currentOffset: Int = offset

    val version = BytesUtils.getReversedInt(transactionBytes, currentOffset)
    currentOffset += 4

    // parse inputs
    val inputsNumber: VarInt = BytesUtils.getReversedVarInt(transactionBytes, currentOffset)
    currentOffset += inputsNumber.size()

    for (i <- 1 to inputsNumber.value().intValue()) {
      currentOffset += MainchainTransactionInput.parse(transactionBytes, currentOffset).size
    }

    // parse outputs
    val outputsNumber: VarInt = BytesUtils.getReversedVarInt(transactionBytes, currentOffset)
    currentOffset += outputsNumber.size()

    for (i <- 1 to outputsNumber.value().intValue()) {
      currentOffset += MainchainTransactionOutput.parse(transactionBytes, currentOffset).size
    }

    if(version == SC_TX_VERSION) {
      var crosschainOutputs: Seq[MainchainTxCrosschainOutput] = Seq()

      // parse SidechainCreation outputs
      val creationOutputsNumber: VarInt = BytesUtils.getReversedVarInt(transactionBytes, currentOffset)
      currentOffset += creationOutputsNumber.size()
      for (i <- 1 to creationOutputsNumber.value().intValue()) {
        val output = MainchainTxSidechainCreationCrosschainOutput.create(transactionBytes, currentOffset).get
        currentOffset += output.size
        crosschainOutputs = crosschainOutputs :+ output
      }

      // dummy parse of Certifier lock amount for merge_support_branch
      val certifierLockOutputsNumber: VarInt = BytesUtils.getReversedVarInt(transactionBytes, currentOffset)
      currentOffset += certifierLockOutputsNumber.size()

      // parse Forward Transfer outputs
      val forwardTransferOutputsNumber: VarInt = BytesUtils.getReversedVarInt(transactionBytes, currentOffset)
      currentOffset += forwardTransferOutputsNumber.size()
      for (i <- 1 to forwardTransferOutputsNumber.value().intValue()) {
        val output = MainchainTxForwardTransferCrosschainOutput.create(transactionBytes, currentOffset).get
        currentOffset += MainchainTxForwardTransferCrosschainOutput.FORWARD_TRANSFER_OUTPUT_SIZE
        crosschainOutputs = crosschainOutputs :+ output
      }

      // put data into crosschainOutputsMap
      crosschainOutputsMap = crosschainOutputs.groupBy[ByteArrayWrapper](output => new ByteArrayWrapper(output.sidechainId))
    }

    // parse lockTime. TO DO: check. maybe need to use getInt
    BytesUtils.getReversedInt(transactionBytes, currentOffset)
    currentOffset += 4


    // check if it's a transaction with JoinSplits and parse them if need
    // Note: actually joinsplit data is not important for us. So we will just parse it for knowing its size
    if (version >= PHGR_TX_VERSION || version == GROTH_TX_VERSION) {
      val joinSplitsNumber: VarInt = BytesUtils.getVarInt(transactionBytes, currentOffset)
      currentOffset += joinSplitsNumber.size()
      if(joinSplitsNumber.value().intValue() != 0) {
        var joinSplitsOffset: Int = 8 + // int64_t vpub_old
          8 + // int64_t vpub_new
          32 + // uint256 anchor
          32 * 2 + // std::array<uint256, ZC_NUM_JS_INPUTS> nullifiers, where ZC_NUM_JS_INPUTS = 2
          32 * 2 + // std::array<uint256, ZC_NUM_JS_OUTPUTS> commitments, where ZC_NUM_JS_OUTPUTS = 2
          32 + // uint256 ephemeralKey
          32 + // uint256 randomSeed
          32 * 2 // std::array<uint256, ZC_NUM_JS_INPUTS> macs

        if (version >= PHGR_TX_VERSION) // parse PHGRProof
          joinSplitsOffset += 33 * 7 + 65 // PHGRProof consists of 7 CompressedG1  (33 bytes each) + 1 CompressedG2 (65 bytes)
        else // version == GROTH_TX_VERSION -> parse GrothProof
          joinSplitsOffset += 192 // typedef std::array<unsigned char, GROTH_PROOF_SIZE> GrothProof, where GROTH_PROOF_SIZE = 48 + 96 + 48

        joinSplitsOffset += 601 * 2 // std::array<ZCNoteEncryption::Ciphertext, ZC_NUM_JS_OUTPUTS>, where typedef std::array<unsigned char, CLEN> Ciphertext and CLEN = 1 + 8 + 32 + 32 + 512 + 16

        joinSplitsOffset *= joinSplitsNumber.value().intValue()

        joinSplitsOffset += 32 + 64 // uint256 joinSplitPubKey;  +  typedef boost::array<unsigned char, 64> joinsplit_sig_t


        currentOffset += joinSplitsOffset
      }
    }

    new MainchainTransaction(transactionBytes.slice(offset, currentOffset), version, crosschainOutputsMap)
  }
}