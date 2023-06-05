package io.horizen.account.sc2sc

import com.google.common.primitives.Bytes
import io.horizen.account.state.NativeSmartContractMsgProcessor.NULL_HEX_STRING_32
import io.horizen.account.state._
import io.horizen.account.utils.WellKnownAddresses.SC_TX_COMMITMENT_TREE_ROOT_HASH_SMART_CONTRACT_ADDRESS
import io.horizen.evm.Address
import sparkz.crypto.hash.Keccak256
import sparkz.utils.ByteArray

trait ScTxCommitmentTreeRootHashMessageProvider {
  def addScTxCommitmentTreeRootHash(hash: Array[Byte], view: BaseAccountStateView): Unit
  def doesScTxCommitmentTreeRootHashExist(hash: Array[Byte], view: BaseAccountStateView): Boolean
}

/**
 * This fake smart contract is responsible to save and retrieve the scTxCommitmentTreeRootHash and cannot be called
 */
object ScTxCommitmentTreeRootHashMessageProcessor
  extends NativeSmartContractMsgProcessor with ScTxCommitmentTreeRootHashMessageProvider {
  override val contractAddress: Address = SC_TX_COMMITMENT_TREE_ROOT_HASH_SMART_CONTRACT_ADDRESS
  override val contractCode: Array[Byte] = Keccak256.hash("ScTxCommitmentTreeRootHashSmartContractCode")

  /**
   * The process just raise an exception because this smart contract is not meant to be called outside
   */
  override def process(msg: Message, view: BaseAccountStateView, gas: GasPool, blockContext: BlockContext): Array[Byte] =
    throw new ExecutionRevertedException("Cannot call ScTxCommitmentTreeRootHashMessageProcessor directly")

  override def addScTxCommitmentTreeRootHash(hash: Array[Byte], view: BaseAccountStateView): Unit =
    view.updateAccountStorage(contractAddress, getScTxCommitmentTreeRootHashKey(hash), hash)

  override def doesScTxCommitmentTreeRootHashExist(hash: Array[Byte], view: BaseAccountStateView): Boolean =
    view.getAccountStorage(contractAddress, getScTxCommitmentTreeRootHashKey(hash)).nonEmpty

  private[sc2sc] def getScTxCommitmentTreeRootHashKey(hash: Array[Byte]): Array[Byte] =
    calculateKey(Bytes.concat("scCommitmentTreeRootHash".getBytes, hash))

  private def calculateKey(keySeed: Array[Byte]): Array[Byte] =
    Keccak256.hash(keySeed)
}
