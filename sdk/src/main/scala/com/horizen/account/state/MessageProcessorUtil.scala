package com.horizen.account.state

import com.horizen.params.NetworkParams

import scala.collection.JavaConverters.asScalaBufferConverter

object MessageProcessorUtil {
  def getMessageProcessorSeq(params: NetworkParams, customMessageProcessors: java.util.List[MessageProcessor]): Seq[MessageProcessor] = {
    Seq(
      EoaMessageProcessor,
      WithdrawalMsgProcessor,
      ForgerStakeMsgProcessor(params),
    ) ++ customMessageProcessors.asScala
  }
}
