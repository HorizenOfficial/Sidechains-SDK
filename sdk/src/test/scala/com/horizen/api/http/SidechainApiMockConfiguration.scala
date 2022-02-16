package com.horizen.api.http

import com.horizen.consensus.ConsensusEpochAndSlot
import com.horizen.forge.ForgingInfo
import scorex.util.ModifierId

import scala.collection.mutable
import scala.util.{Failure, Try}

class SidechainApiMockConfiguration {

  private var should_nodeViewHolder_LocallyGeneratedSecret_reply: Boolean = true
  private var should_history_getBlockById_return_value: Boolean = true
  private var should_history_getBlockIdByHeight_return_value: Boolean = true
  private var should_history_getCurrentHeight_return_value = true
  private var should_blockActor_SubmitSidechainBlock_reply = true
  private var should_blockActor_GenerateSidechainBlocks_reply = true
  private var should_history_getBlockInfoById_return_value: Boolean = true
  var should_blockActor_StopForging_reply: Boolean = true
  var should_blockActor_StartForging_reply: Boolean = true
  var should_blockActor_ForgingInfo_reply: Try[ForgingInfo] = Failure(new NullPointerException)

  val blockActor_ForgingEpochAndSlot_reply: mutable.Map[ConsensusEpochAndSlot, Try[ModifierId]] = mutable.Map[ConsensusEpochAndSlot, Try[ModifierId]]()
  private var should_peerManager_GetAllPeers_reply: Boolean = true
  private var should_networkController_GetConnectedPeers_reply = true
  private var should_peerManager_GetBlacklistedPeers_reply = true
  private var should_history_getBestMainchainBlockReferenceInfo_return_value = true
  private var should_history_getMainchainBlockReferenceInfoByMainchainBlockHeight_return_value = true
  private var should_history_getMainchainBlockReferenceInfoByHash_return_value = true
  private var should_history_getMainchainBlockReferenceByHash_return_value = true
  private var should_history_getTransactionsSortedByFee_return_value = true
  private var should_transactionActor_BroadcastTransaction_reply = true
  private var should_memPool_searchTransactionInMemoryPool_return_value = true
  private var should_history_searchTransactionInBlockchain_return_value = true
  private var should_history_searchTransactionInBlock_return_value = true
  private var should_nodeViewHolder_GetDataFromCurrentSidechainNodeView_reply = true
  private var should_nodeViewHolder_ApplyFunctionOnNodeView_reply = true
  private var should_nodeViewHolder_ApplyBiFunctionOnNodeView_reply = true
  private var should_nodeViewHolder_GetStorageVersions_reply: Boolean = true

  def getShould_nodeViewHolder_LocallyGeneratedSecret_reply(): Boolean = should_nodeViewHolder_LocallyGeneratedSecret_reply

  def setShould_nodeViewHolder_LocallyGeneratedSecret_reply(value: Boolean): Unit = should_nodeViewHolder_LocallyGeneratedSecret_reply = value

  def getShould_nodeViewHolder_GetStorageVersions_reply(): Boolean = should_nodeViewHolder_GetStorageVersions_reply

  def setShould_nodeViewHolder_GetStorageVersions_reply(value: Boolean): Unit = should_nodeViewHolder_GetStorageVersions_reply = value

  def getShould_history_getBlockById_return_value(): Boolean = should_history_getBlockById_return_value

  def setShould_history_getBlockById_return_value(value: Boolean): Unit = should_history_getBlockById_return_value = value

  def getShould_history_getBlockIdByHeight_return_value(): Boolean = should_history_getBlockIdByHeight_return_value

  def setShould_history_getBlockIdByHeight_return_value(value: Boolean): Unit = should_history_getBlockIdByHeight_return_value = value

  def getShould_history_getCurrentHeight_return_value(): Boolean = should_history_getCurrentHeight_return_value

  def setShould_history_getCurrentHeight_return_value(value: Boolean): Unit = should_history_getCurrentHeight_return_value = value

  def getShould_blockActor_SubmitSidechainBlock_reply(): Boolean = should_blockActor_SubmitSidechainBlock_reply

  def setShould_blockActor_SubmitSidechainBlock_reply(value: Boolean): Unit = should_blockActor_SubmitSidechainBlock_reply = value

  def getShould_blockActor_GenerateSidechainBlocks_reply(): Boolean = should_blockActor_GenerateSidechainBlocks_reply

  def setShould_blockActor_GenerateSidechainBlocks_reply(value: Boolean): Unit = should_blockActor_GenerateSidechainBlocks_reply = value

  def getSshould_peerManager_GetAllPeers_reply(): Boolean = should_peerManager_GetAllPeers_reply

  def setShould_peerManager_GetAllPeers_reply(value: Boolean): Unit = should_peerManager_GetAllPeers_reply = value

  def getShould_networkController_GetConnectedPeers_reply(): Boolean = should_networkController_GetConnectedPeers_reply

  def setShould_networkController_GetConnectedPeers_reply(value: Boolean): Unit = should_networkController_GetConnectedPeers_reply = value

  def getShould_peerManager_GetBlacklistedPeers_reply(): Boolean = should_peerManager_GetBlacklistedPeers_reply

  def setShould_peerManager_GetBlacklistedPeers_reply(value: Boolean): Unit = should_peerManager_GetBlacklistedPeers_reply = value

  def getShould_history_getBestMainchainBlockReferenceInfo_return_value(): Boolean = should_history_getBestMainchainBlockReferenceInfo_return_value

  def setShould_history_getBestMainchainBlockReferenceInfo_return_value(value: Boolean): Unit = should_history_getBestMainchainBlockReferenceInfo_return_value = value

  def getShould_history_getMainchainBlockReferenceInfoByMainchainBlockHeight_return_value(): Boolean = should_history_getMainchainBlockReferenceInfoByMainchainBlockHeight_return_value

  def setShould_history_getMainchainBlockReferenceInfoByMainchainBlockHeight_return_value(value: Boolean): Unit = should_history_getMainchainBlockReferenceInfoByMainchainBlockHeight_return_value = value

  def getShould_history_getMainchainBlockReferenceInfoByHash_return_value(): Boolean = should_history_getMainchainBlockReferenceInfoByHash_return_value

  def setShould_history_getMainchainBlockReferenceInfoByHash_return_value(value: Boolean): Unit = should_history_getMainchainBlockReferenceInfoByHash_return_value = value

  def getShould_history_getMainchainBlockReferenceByHash_return_value(): Boolean = should_history_getMainchainBlockReferenceByHash_return_value

  def setShould_history_getMainchainBlockReferenceByHash_return_value(value: Boolean): Unit = should_history_getMainchainBlockReferenceByHash_return_value = value

  def getShould_history_getTransactionsSortedByFee_return_value(): Boolean = should_history_getTransactionsSortedByFee_return_value

  def setShould_history_getTransactionsSortedByFee_return_value(value: Boolean): Unit = should_history_getTransactionsSortedByFee_return_value = value

  def getShould_transactionActor_BroadcastTransaction_reply(): Boolean = should_transactionActor_BroadcastTransaction_reply

  def setShould_transactionActor_BroadcastTransaction_reply(value: Boolean): Unit = should_transactionActor_BroadcastTransaction_reply = value

  def getShould_memPool_searchTransactionInMemoryPool_return_value(): Boolean = should_memPool_searchTransactionInMemoryPool_return_value

  def setShould_memPool_searchTransactionInMemoryPool_return_value(value: Boolean): Unit = should_memPool_searchTransactionInMemoryPool_return_value = value

  def getShould_history_searchTransactionInBlockchain_return_value(): Boolean = should_history_searchTransactionInBlockchain_return_value

  def setShould_history_searchTransactionInBlockchain_return_value(value: Boolean): Unit = should_history_searchTransactionInBlockchain_return_value = value

  def getShould_history_searchTransactionInBlock_return_value(): Boolean = should_history_searchTransactionInBlock_return_value

  def setShould_history_searchTransactionInBlock_return_value(value: Boolean): Unit = should_history_searchTransactionInBlock_return_value = value

  def getShould_nodeViewHolder_GetDataFromCurrentSidechainNodeView_reply(): Boolean = should_nodeViewHolder_GetDataFromCurrentSidechainNodeView_reply

  def setShould_nodeViewHolder_GetDataFromCurrentSidechainNodeView_reply(value: Boolean): Unit = should_nodeViewHolder_GetDataFromCurrentSidechainNodeView_reply = value

  def getShould_nodeViewHolder_ApplyFunctionOnNodeView_reply(): Boolean = should_nodeViewHolder_ApplyFunctionOnNodeView_reply

  def setShould_nodeViewHolder_ApplyFunctionOnNodeView_reply(value: Boolean): Unit = should_nodeViewHolder_ApplyFunctionOnNodeView_reply = value

  def getShould_nodeViewHolder_ApplyBiFunctionOnNodeView_reply(): Boolean = should_nodeViewHolder_ApplyBiFunctionOnNodeView_reply

  def setShould_nodeViewHolder_ApplyBiFunctionOnNodeView_reply(value: Boolean): Unit = should_nodeViewHolder_ApplyBiFunctionOnNodeView_reply = value

  def getShould_history_getBlockInfoById_return_value(): Boolean = should_history_getBlockInfoById_return_value

  def setShould_history_getBlockInfoById_return_value(value: Boolean): Unit = should_history_getBlockInfoById_return_value = value


}