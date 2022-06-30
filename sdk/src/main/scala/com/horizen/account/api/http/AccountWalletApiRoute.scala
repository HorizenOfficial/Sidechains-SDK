package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.account.api.http.AccountWalletRestScheme.RespCreatePrivateKeySecp256k1
import com.horizen.{AbstractSidechainNodeViewHolder, SidechainTypes}
import com.horizen.account.api.http.SidechainWalletErrorResponse.ErrorNotImplemented
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.secret.PrivateKeySecp256k1Creator
import com.horizen.api.http.SidechainWalletErrorResponse.ErrorSecretNotAdded
import com.horizen.api.http.{ApiResponseUtil, ErrorResponse, SidechainApiRoute, SuccessResponse}
import com.horizen.box.Box
import com.horizen.node.NodeWalletBase
import com.horizen.proposition.{Proposition, VrfPublicKey}
import com.horizen.serialization.Views
import scorex.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

case class AccountWalletApiRoute(override val settings: RESTApiSettings,
                                   sidechainNodeViewHolderRef: ActorRef)(implicit val context: ActorRefFactory, override val ec: ExecutionContext)
  extends SidechainApiRoute[
    SidechainTypes#SCAT,
    AccountBlockHeader,
    AccountBlock,
    NodeAccountHistory,
    NodeAccountState,
    NodeWalletBase,
    NodeAccountMemoryPool,
    AccountNodeView] {

  override val route: Route = pathPrefix("wallet") {
     allPublicKeys ~ createPrivateKeySecp256k1
  }

  override implicit val tag: ClassTag[AccountNodeView] = ClassTag[AccountNodeView](classOf[AccountNodeView])


  /**
    * Returns the list of all wallet’s propositions (public keys). Filter propositions of the given type
    */
  def allPublicKeys: Route = (post & path("allPublicKeys")) {
    ApiResponseUtil.toResponse(ErrorNotImplemented("Method not implemented", JOptional.empty()))

  }

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
  private[api] case class ReqAllBoxes(boxTypeClass: Option[String], excludeBoxIds: Option[Seq[String]])

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllBoxes(boxes: List[Box[Proposition]]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqBalance(boxType: String)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespBalance(balance: Long) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCreatePrivateKeySecp256k1(proposition: Proposition) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCreateVrfSecret(proposition: VrfPublicKey) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqAllPropositions(proptype: Option[String])

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllPublicKeys(propositions: Seq[Proposition]) extends SuccessResponse

}

object SidechainWalletErrorResponse {

  case class ErrorNotImplemented(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0301"
  }

}