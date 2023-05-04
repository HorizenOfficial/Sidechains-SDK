package io.horizen.api.http

import akka.actor._
import com.google.common.util.concurrent.RateLimiter
import sparkz.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedTransaction
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.{FailedTransaction, SuccessfulTransaction}
import sparkz.util.SparkzLogging

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext

class SidechainTransactionRateLimiterActor(nodeViewHolderRef: ActorRef, minThroughput: Int = 10)(implicit ec: ExecutionContext)
  extends Actor with SparkzLogging {

  private val transactionMap : TrieMap[String, Long] = TrieMap()
  private val rateLimiter: RateLimiter = RateLimiter.create(minThroughput)
  private var averageProcessingTimeMs: Long = 0

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[SuccessfulTransaction[_]])
    context.system.eventStream.subscribe(self, classOf[FailedTransaction])
  }

  override def postStop(): Unit = {
    log.debug("SidechainTransactionRateLimiterActor actor is stopping...")
    super.postStop()
  }

  protected def locallyGeneratedTransaction: Receive = {
    case LocallyGeneratedTransaction(transaction) =>
      if (averageProcessingTimeMs > 2000 && !rateLimiter.tryAcquire()) {
        context.system.eventStream.publish(
          FailedTransaction(
            transaction.id,
            new IllegalArgumentException("Rate limiting applied - node is out of capacity."),
            immediateFailure = true
          )
        )
      } else {
        val startTime: Long = System.currentTimeMillis()
        transactionMap(transaction.id) = startTime
        nodeViewHolderRef ! LocallyGeneratedTransaction(transaction)
      }
  }

  protected def sidechainNodeViewHolderEvents: Receive = {
    case SuccessfulTransaction(transaction) =>
      transactionMap.remove(transaction.id) match {
        case Some(startTime) => updateProcessingTime(startTime)
        case None =>
      }
    case FailedTransaction(transactionId, throwable, _) =>
      transactionMap.remove(transactionId) match {
        case Some(startTime) => updateProcessingTime(startTime)
        case None =>
      }
  }

  private def updateProcessingTime(startTime: Long): Unit = {
    val elapsed: Long = System.currentTimeMillis() - startTime
    averageProcessingTimeMs = (averageProcessingTimeMs * 0.9).toLong + (elapsed * 0.1).toLong
  }

  override def receive: Receive = {
    locallyGeneratedTransaction orElse
      sidechainNodeViewHolderEvents orElse {
      case message: Any => log.error("SidechainTransactionRateLimiterActor received strange message: " + message)
    }
  }
}

object SidechainTransactionRateLimiterActorRef {
  def props(sidechainNodeViewHolderRef: ActorRef, minThroughput: Int)
           (implicit ec: ExecutionContext): Props =
    Props(new SidechainTransactionRateLimiterActor(sidechainNodeViewHolderRef, minThroughput))

  def apply(sidechainNodeViewHolderRef: ActorRef, minThroughput: Int)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(sidechainNodeViewHolderRef, minThroughput))
}

