package com.horizen.account.websocket.data

import com.horizen.account.receipt.Bloom
import com.horizen.evm.interop.EvmLog
import com.horizen.evm.utils.Hash
import com.horizen.utils.BytesUtils
import org.web3j.utils.Numeric

import jakarta.websocket.Session

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

  def filterTransactionLogs(log: EvmLog): Boolean = {
    if (address.isDefined && !address.get.contains(log.address.toString))
      false
    else
      filterTransactionLogsByTopic(log.topics)
  }

  def filterTransactionLogsByTopic(logTopics: Array[Hash]): Boolean = {
    if (topics.isDefined && topics.get.length > 0 && !topics.get.exists(topic => logTopics.contains(new Hash(BytesUtils.fromHexString(Numeric.cleanHexPrefix(topic))))))
      false
    else
      true
  }

}
