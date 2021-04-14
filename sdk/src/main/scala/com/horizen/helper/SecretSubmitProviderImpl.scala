package com.horizen.helper
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.LocallyGeneratedSecret
import com.horizen.secret.Secret

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

class SecretSubmitProviderImpl(sidechainNodeViewHolderRef: ActorRef) extends SecretSubmitProvider {

  implicit val timeout: Timeout = 20 seconds

  @throws(classOf[IllegalArgumentException])
  override def submitSecret(s: Secret): Unit = {
    Await.ready(sidechainNodeViewHolderRef ? LocallyGeneratedSecret(s), timeout.duration).value.get match {
      case Success(res: Try[Unit]) =>
        res match {
          case Success(_) =>
          case Failure(exception) => throw new IllegalArgumentException(exception)
        }
      case Failure(exception) => throw new IllegalArgumentException(exception)
    }
  }
}
