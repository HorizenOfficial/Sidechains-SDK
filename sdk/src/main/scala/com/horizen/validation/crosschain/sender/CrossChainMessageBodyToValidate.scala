package com.horizen.validation.crosschain.sender

import com.horizen.block.SidechainBlock
import com.horizen.validation.crosschain.CrossChainBodyToValidate

class CrossChainMessageBodyToValidate(scBlock: SidechainBlock) extends CrossChainBodyToValidate[SidechainBlock] {
  override def getBodyToValidate: SidechainBlock = scBlock
}
