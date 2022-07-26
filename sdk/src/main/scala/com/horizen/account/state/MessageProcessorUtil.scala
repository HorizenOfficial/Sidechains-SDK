package com.horizen.account.state

import com.horizen.params.NetworkParams

object MessageProcessorUtil {
  def getMessageProcessorSeq(params: NetworkParams, customMessageProcessors: Seq[MessageProcessor]): Seq[MessageProcessor] = {
    Seq(
      EoaMessageProcessor,
      WithdrawalMsgProcessor,
      ForgerStakeMsgProcessor(params),
    ) ++ customMessageProcessors
  }
}
