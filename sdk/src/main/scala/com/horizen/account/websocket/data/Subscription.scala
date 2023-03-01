package com.horizen.account.websocket.data

import com.horizen.account.receipt.{Bloom, EthereumConsensusDataLog}
import com.horizen.utils.BytesUtils
import io.horizen.evm.{Address, Hash}
import jakarta.websocket.Session

import java.math.BigInteger

trait BaseSubscription {
  def session: Session
  def subscriptionId: BigInteger
}

case class Subscription(session: Session, subscriptionId: BigInteger) extends BaseSubscription
case class SubscriptionWithFilter(session: Session, subscriptionId: BigInteger,
                                  address: Option[Array[Address]],
                                  topics: Option[Array[Hash]]) extends BaseSubscription {

  def checkSubscriptionInBloom(bloom: Bloom): Boolean = {
    if (address.isDefined && !address.get.exists(addr => bloom.test(BytesUtils.fromHexString(addr.toStringNoPrefix))))
      false
    else
      filterBlockLogsByTopic(bloom)
  }

  def filterBlockLogsByTopic(bloom: Bloom): Boolean = {
    if (topics.isDefined && !topics.get.exists(topic => bloom.test(BytesUtils.fromHexString(topic.toStringNoPrefix))))
      false
    else
      true
  }

  def filterTransactionLogs(log: EthereumConsensusDataLog): Boolean = {
    if (address.isDefined && !address.get.contains(log.address))
      false
    else
      filterTransactionLogsByTopic(log.topics)
  }

  def filterTransactionLogsByTopic(logTopics: Array[Hash]): Boolean = {
    if (topics.isDefined && topics.get.length > 0 && !topics.get.exists(topic => logTopics.contains(new Hash(BytesUtils.fromHexString(topic.toStringNoPrefix)))))
      false
    else
      true
  }

}
