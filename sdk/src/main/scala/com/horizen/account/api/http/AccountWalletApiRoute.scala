package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.SidechainTypes
import com.horizen.account.api.http.SidechainWalletErrorResponse.ErrorNotImplemented
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.api.http.{ApiResponseUtil, ErrorResponse, SidechainApiRoute, SuccessResponse}
import com.horizen.box.Box
import com.horizen.node.NodeWalletBase
import com.horizen.proposition.{Proposition, VrfPublicKey}
import com.horizen.serialization.Views
import scorex.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

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
     allPublicKeys
  }

  override implicit val tag: ClassTag[AccountNodeView] = ClassTag[AccountNodeView](classOf[AccountNodeView])


  /**
    * Returns the list of all wallet’s propositions (public keys). Filter propositions of the given type
    */
  def allPublicKeys: Route = (post & path("allPublicKeys")) {
    ApiResponseUtil.toResponse(ErrorNotImplemented("Method not implemented", JOptional.empty()))

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
  private[api] case class RespCreatePrivateKey25519(proposition: Proposition) extends SuccessResponse

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