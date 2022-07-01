package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.account.api.http.AccountTransactionErrorResponse.ErrorInsufficientBalance
import com.horizen.account.api.http.AccountWalletRestScheme.{ReqGetBalance, RespCreatePrivateKeySecp256k1, RespGetBalance}
import com.horizen.{AbstractSidechainNodeViewHolder, SidechainTypes}
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.secret.PrivateKeySecp256k1Creator
import com.horizen.api.http.WalletBaseErrorResponse.ErrorSecretNotAdded
import com.horizen.api.http.{ApiResponseUtil, SuccessResponse, WalletBaseApiRoute}
import com.horizen.node.NodeWalletBase
import com.horizen.proposition.Proposition
import com.horizen.serialization.Views
import com.horizen.utils.BytesUtils

import scorex.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

import com.horizen.api.http.JacksonSupport._


case class AccountWalletApiRoute(override val settings: RESTApiSettings,
                                 sidechainNodeViewHolderRef: ActorRef)(implicit override val context: ActorRefFactory, override val ec: ExecutionContext)
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
    // some of these methods are in the base class
    createPrivateKey25519 ~ createVrfSecret ~ allPublicKeys ~ createPrivateKeySecp256k1 ~ getBalance
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

  /**
   * Create new secret and return corresponding address (public key)
   */
  def getBalance: Route = (post & path("getBalance")) {
    entity(as[ReqGetBalance]) { body =>
      applyOnNodeView { sidechainNodeView =>
        if (body.address.isDefined) {
          val fromAddr = new AddressProposition(BytesUtils.fromHexString(body.address.get))
          val fromBalance = sidechainNodeView.getNodeState.getBalance(fromAddr.address())
          ApiResponseUtil.toResponse(RespGetBalance(fromBalance.get.toString))
        } else {
          ApiResponseUtil.toResponse(ErrorInsufficientBalance("ErrorInsufficientBalance", JOptional.empty()))

        }
      }
    }
  }
}

object AccountWalletRestScheme {
  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCreatePrivateKeySecp256k1(proposition: Proposition) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespGetBalance(balance: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqGetBalance(address: Option[String]) {
    require(address.nonEmpty, "Empty address")
  }
}
