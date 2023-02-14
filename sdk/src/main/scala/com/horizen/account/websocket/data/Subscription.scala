package com.horizen.account.websocket.data

import com.horizen.account.receipt.Bloom
import com.horizen.evm.interop.EvmLog
import com.horizen.evm.utils.Hash
import com.horizen.utils.BytesUtils
import org.web3j.utils.Numeric

import javax.websocket.Session

trait BaseSubscription {
  def session: Session
  def subscriptionId: String
}

case class Subscription(session: Session, subscriptionId: String) extends BaseSubscription {}
case class SubscriptionWithFilter(session: Session, subscriptionId: String,
                                  address: Option[Array[String]],
                                  topics: Option[Array[String]]) extends BaseSubscription {

  def checkSubscriptionInBloom(bloom: Bloom): Boolean = {
    if (address.isDefined && !address.get.exists(addr => bloom.test(BytesUtils.fromHexString(Numeric.cleanHexPrefix(addr)))))
      false
    else
      filterBlockLogsByTopic(bloom)
  }

  def filterBlockLogsByTopic(bloom: Bloom): Boolean = {
    if (topics.isDefined && !topics.get.exists(topic => bloom.test(BytesUtils.fromHexString(Numeric.cleanHexPrefix(topic)))))
      false
    else
      true
  }

  def filterTransactionLogs(log: EvmLog): Option[Array[String]] = {
    if (address.isDefined && !address.get.contains(log.address.toString))
      Option.empty
    else
      filterTransactionLogsByTopic(log.topics)
  }

  def filterTransactionLogsByTopic(logTopics: Array[Hash]): Option[Array[String]] = {
    topics match {
      case Some(topicFilters) =>
        val matchedTopics = topicFilters.filter(topic => logTopics.contains(Hash.fromBytes(BytesUtils.fromHexString(topic.substring(2)))))
        Some(matchedTopics)
      case None =>
        Some(logTopics.map(topic => BytesUtils.toHexString(topic.toBytes)))
    }
  }

}
