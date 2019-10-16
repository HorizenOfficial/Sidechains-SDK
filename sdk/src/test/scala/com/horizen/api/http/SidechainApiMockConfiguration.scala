package com.horizen.api.http

class SidechainApiMockConfiguration {

  private var should_nodeViewHolder_LocallyGeneratedSecret_reply: Boolean = true
  private var should_history_getBlockById_return_value: Boolean = true
  private var should_history_getBlockIdByHeight_return_value: Boolean = true
  private var should_history_getCurrentHeight_return_value = true
  private var should_forger_TryGetBlockTemplate_reply = true
  private var should_blockActor_SubmitSidechainBlock_reply = true
  private var should_blockActor_GenerateSidechainBlocks_reply = true
  private var should_peerManager_GetAllPeers_reply: Boolean = true
  private var should_networkController_GetConnectedPeers_reply = true
  private var should_peerManager_GetBlacklistedPeers_reply = true
  private var should_history_getBestMainchainBlockReferenceInfo_return_value = true
  private var should_history_getMainchainBlockReferenceInfoByMainchainBlockHeight_return_value = true
  private var should_history_getMainchainBlockReferenceInfoByHash_return_value = true
  private var should_history_getMainchainBlockReferenceByHash_return_value = true

  def getShould_nodeViewHolder_LocallyGeneratedSecret_reply(): Boolean = should_nodeViewHolder_LocallyGeneratedSecret_reply

  def setShould_nodeViewHolder_LocallyGeneratedSecret_reply(value: Boolean): Unit = should_nodeViewHolder_LocallyGeneratedSecret_reply = value

  def getShould_history_getBlockById_return_value(): Boolean = should_history_getBlockById_return_value

  def setShould_history_getBlockById_return_value(value: Boolean): Unit = should_history_getBlockById_return_value = value

  def getShould_history_getBlockIdByHeight_return_value(): Boolean = should_history_getBlockIdByHeight_return_value

  def setShould_history_getBlockIdByHeight_return_value(value: Boolean): Unit = should_history_getBlockIdByHeight_return_value = value

  def getShould_history_getCurrentHeight_return_value(): Boolean = should_history_getCurrentHeight_return_value

  def setShould_history_getCurrentHeight_return_value(value: Boolean): Unit = should_history_getCurrentHeight_return_value = value

  def getShould_forger_TryGetBlockTemplate_reply(): Boolean = should_forger_TryGetBlockTemplate_reply

  def setShould_forger_TryGetBlockTemplate_reply(value: Boolean): Unit = should_forger_TryGetBlockTemplate_reply = value

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
}