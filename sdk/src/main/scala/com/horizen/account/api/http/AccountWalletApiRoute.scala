package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.account.api.http.AccountWalletRestScheme.RespCreatePrivateKeySecp256k1
import com.horizen.{AbstractSidechainNodeViewHolder, SidechainTypes}
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.secret.PrivateKeySecp256k1Creator
import com.horizen.api.http.SidechainWalletErrorResponse.ErrorSecretNotAdded
import com.horizen.api.http.{ApiResponseUtil, ErrorResponse, SuccessResponse, WalletBaseApiRoute}
import com.horizen.node.NodeWalletBase
import com.horizen.proposition.Proposition
import com.horizen.serialization.Views
import scorex.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

case class AccountWalletApiRoute(override val settings: RESTApiSettings,
                                 override val sidechainNodeViewHolderRef: ActorRef)(implicit override val context: ActorRefFactory, override val ec: ExecutionContext)
  extends WalletBaseApiRoute[
    SidechainTypes#SCAT,
    AccountBlockHeader,
    AccountBlock,
    NodeAccountHistory,
    NodeAccountState,
    NodeWalletBase,
    NodeAccountMemoryPool,
    AccountNodeView] (settings, sidechainNodeViewHolderRef) {

  override val route: Route = pathPrefix("wallet") {
    createPrivateKey25519 ~ createVrfSecret ~ allPublicKeys ~ createPrivateKeySecp256k1
  }

  override implicit val tag: ClassTag[AccountNodeView] = ClassTag[AccountNodeView](classOf[AccountNodeView])

  /**
   * Create new secret and return corresponding address (public key)
   */
  def createPrivateKeySecp256k1: Route = (post & path("createPrivateKeySecp256k1")) {
    withNodeView { sidechainNodeView =>
      val wallet = sidechainNodeView.getNodeWallet
      val secret = PrivateKeySecp256k1Creator.getInstance().generateNextSecret(wallet)
      val future = sidechainNodeViewHolderRef ? AbstractSidechainNodeViewHolder.ReceivableMessages.LocallyGeneratedSecret(secret)
      Await.result(future, timeout.duration).asInstanceOf[Try[Unit]] match {
        case Success(_) =>
          ApiResponseUtil.toResponse(RespCreatePrivateKeySecp256k1(secret.publicImage()))
        case Failure(e) =>
          ApiResponseUtil.toResponse(ErrorSecretNotAdded("Failed to create key pair.", JOptional.of(e)))
      }
    }
  }
 }

object AccountWalletRestScheme {
  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCreatePrivateKeySecp256k1(proposition: Proposition) extends SuccessResponse
}

object SidechainWalletErrorResponse {

  case class ErrorNotImplemented(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0301"
  }

}