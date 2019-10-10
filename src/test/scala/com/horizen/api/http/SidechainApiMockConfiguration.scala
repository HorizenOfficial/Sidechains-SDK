package com.horizen.api.http

class SidechainApiMockConfiguration {

  private var should_wallet_addSecret_return_value: Boolean = true
  private var should_history_getBlockById_return_value: Boolean = true
  private var should_history_getBlockIdByHeight_return_value: Boolean = true
  private var should_history_getCurrentHeight_return_value = true
  private var should_forger_TryGetBlockTemplate_reply = true
  private var should_blockActor_SubmitSidechainBlock_reply = true
  private var should_blockActor_GenerateSidechainBlocks_reply = true

  def getShould_wallet_addSecret_return_value(): Boolean = should_wallet_addSecret_return_value

  def setShould_wallet_addSecret_return_value(value: Boolean): Unit = should_wallet_addSecret_return_value = value

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

}
