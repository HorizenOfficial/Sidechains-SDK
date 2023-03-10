package io.horizen.account.websocket.data

import io.horizen.account.api.rpc.types.FilterQuery
import jakarta.websocket.Session

import java.math.BigInteger

trait BaseSubscription {
  def session: Session
  def subscriptionId: BigInteger
}

case class Subscription(session: Session, subscriptionId: BigInteger) extends BaseSubscription

case class SubscriptionWithFilter(session: Session, subscriptionId: BigInteger,
                                  filter: FilterQuery) extends BaseSubscription
