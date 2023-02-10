package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages
import com.horizen.account.api.http.AccountWalletErrorResponse.ErrorCouldNotGetBalance
import com.horizen.account.api.http.AccountWalletRestScheme.{ReqGetBalance, ReqGetTotalBalance, RespCreatePrivateKeySecp256k1, RespGetBalance}
import com.horizen.{AbstractSidechainNodeViewHolder, SidechainTypes}
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import com.horizen.api.http.WalletBaseErrorResponse.ErrorSecretNotAdded
import com.horizen.api.http.{ApiResponseUtil, ErrorResponse, SuccessResponse, WalletBaseApiRoute}
import com.horizen.node.NodeWalletBase
import com.horizen.proposition.Proposition
import com.horizen.serialization.Views
import com.horizen.utils.BytesUtils
import sparkz.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.WalletBaseRestScheme.{ReqCreateKey, RespCreateVrfSecret}
import com.horizen.secret.{VrfKeyGenerator, VrfSecretKey}

import java.math.BigInteger
import scala.collection.JavaConverters.asScalaBufferConverter


case class AccountWalletApiRoute(override val settings: RESTApiSettings,
                                 sidechainNodeViewHolderRef: ActorRef)(implicit override val context: ActorRefFactory, override val ec: ExecutionContext)
  extends WalletBaseApiRoute[
    SidechainTypes#SCAT,
    AccountBlockHeader,
    AccountBlock,
    AccountFeePaymentsInfo,
    NodeAccountHistory,
    NodeAccountState,
    NodeWalletBase,
    NodeAccountMemoryPool,
    AccountNodeView] (settings, sidechainNodeViewHolderRef) {

  override val route: Route = pathPrefix("wallet") {
    // some of these methods are in the base class
    createPrivateKey25519 ~ createVrfSecret ~ allPublicKeys ~ createPrivateKeySecp256k1 ~ getBalance ~ getTotalBalance
  }

  override implicit val tag: ClassTag[AccountNodeView] = ClassTag[AccountNodeView](classOf[AccountNodeView])

  /**
   * Create new secret and return corresponding address (public key)
   */
  def createPrivateKeySecp256k1: Route = (post & path("createPrivateKeySecp256k1")) {
    withAuth {
      entity(as[ReqCreateKey]) { _ =>
        withNodeView { _ =>
          val secretFuture = sidechainNodeViewHolderRef ? ReceivableMessages.GenerateSecret(PrivateKeySecp256k1Creator.getInstance)
          Await.result(secretFuture, timeout.duration).asInstanceOf[Try[PrivateKeySecp256k1]] match {
            case Success(secret: PrivateKeySecp256k1) =>
              ApiResponseUtil.toResponse(RespCreatePrivateKeySecp256k1(secret.publicImage()))
            case Failure(e) =>
              ApiResponseUtil.toResponse(ErrorSecretNotAdded("Failed to create secret.", JOptional.of(e)))
          }
        }
      }
    }
  }

  /**
   * Check balance of the account.
   * Return 0 if account doesn't exist.
   */
  def getBalance: Route = (post & path("getBalance")) {
    withAuth {
      entity(as[ReqGetBalance]) { body =>
        applyOnNodeView { sidechainNodeView =>
          try {
            val fromAddr = new AddressProposition(BytesUtils.fromHexString(body.address))
            val fromBalance = sidechainNodeView.getNodeState.getBalance(fromAddr.address())
            ApiResponseUtil.toResponse(RespGetBalance(fromBalance))
          }
          catch {
            case e: Exception =>
              ApiResponseUtil.toResponse(ErrorCouldNotGetBalance("Could not get balance", JOptional.of(e)))
          }
        }
      }
    }
  }

  /**
   * Check total balance of the wallet.
   */
  def getTotalBalance: Route = (post & path("getTotalBalance")) {
    withAuth {
      entity(as[ReqGetTotalBalance]) { _ =>
        // TODO add an argument for listing also all addresses balance
        applyOnNodeView { sidechainNodeView =>
          try {
            val wallet = sidechainNodeView.getNodeWallet
            val addressList = wallet.secretsOfType(classOf[PrivateKeySecp256k1])
            if (addressList.isEmpty) {
              return ApiResponseUtil.toResponse(RespGetBalance(BigInteger.ZERO))
            }

            val listOfAddressPropositions = addressList.asScala.map(s =>
              s.publicImage().asInstanceOf[AddressProposition])
            var totalBalance = BigInteger.ZERO
            listOfAddressPropositions.foreach(address =>
              totalBalance = totalBalance.add(sidechainNodeView.getNodeState.getBalance(address.address())))

            ApiResponseUtil.toResponse(RespGetBalance(totalBalance))
          }
          catch {
            case e: Exception =>
              ApiResponseUtil.toResponse(ErrorCouldNotGetBalance("Could not get balance", JOptional.of(e)))
          }
        }
      }
    }
  }

}

object AccountWalletRestScheme {
  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCreatePrivateKeySecp256k1(proposition: Proposition) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespGetBalance(balance: BigInteger) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqGetBalance(address: String) {
    require(address.nonEmpty, "Empty address")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqGetTotalBalance() {
  }
}

object AccountWalletErrorResponse {
  case class ErrorCouldNotGetBalance(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0302"
  }
}
