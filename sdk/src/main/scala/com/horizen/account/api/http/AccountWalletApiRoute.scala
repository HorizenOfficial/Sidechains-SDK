package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.account.api.http.AccountWalletErrorResponse.ErrorCouldNotGetBalance
import com.horizen.account.api.http.AccountWalletRestScheme._
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.WalletBaseErrorResponse.ErrorSecretNotAdded
import com.horizen.api.http.WalletBaseRestScheme.{ReqCreateKey, RespCreatePrivateKey}
import com.horizen.api.http.{ApiResponseUtil, ErrorResponse, SuccessResponse, WalletBaseApiRoute}
import com.horizen.companion.SidechainSecretsCompanion
import com.horizen.node.NodeWalletBase
import com.horizen.serialization.Views
import com.horizen.utils.BytesUtils
import com.horizen.{AbstractSidechainNodeViewHolder, SidechainTypes}
import sparkz.core.settings.RESTApiSettings

import java.math.BigInteger
import java.util.{Optional => JOptional}
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

case class AccountWalletApiRoute(override val settings: RESTApiSettings,
                                 sidechainNodeViewHolderRef: ActorRef,
                                 sidechainSecretsCompanion: SidechainSecretsCompanion)(implicit override val context: ActorRefFactory, override val ec: ExecutionContext)
  extends WalletBaseApiRoute[
    SidechainTypes#SCAT,
    AccountBlockHeader,
    AccountBlock,
    AccountFeePaymentsInfo,
    NodeAccountHistory,
    NodeAccountState,
    NodeWalletBase,
    NodeAccountMemoryPool,
    AccountNodeView](settings, sidechainNodeViewHolderRef, sidechainSecretsCompanion) {

  override val route: Route = pathPrefix("wallet") {
    // some of these methods are in the base class
    createPrivateKey25519 ~ createVrfSecret ~ allPublicKeys ~ createPrivateKeySecp256k1 ~ getBalance ~ getTotalBalance ~
      getAllBalances ~ importSecret ~ exportSecret ~ dumpSecrets ~ importSecrets
  }

  override implicit val tag: ClassTag[AccountNodeView] = ClassTag[AccountNodeView](classOf[AccountNodeView])

  /**
   * Create new secret and return corresponding address (public key)
   */
  def createPrivateKeySecp256k1: Route = (post & path("createPrivateKeySecp256k1")) {
    withBasicAuth {
      _ => {
        entity(as[ReqCreateKey]) { _ =>
          withNodeView { sidechainNodeView =>
            val wallet = sidechainNodeView.getNodeWallet
            val secret = PrivateKeySecp256k1Creator.getInstance().generateNextSecret(wallet)
            val future = sidechainNodeViewHolderRef ? AbstractSidechainNodeViewHolder.ReceivableMessages.LocallyGeneratedSecret(secret)
            Await.result(future, timeout.duration).asInstanceOf[Try[Unit]] match {
              case Success(_) =>
                ApiResponseUtil.toResponse(RespCreatePrivateKey(secret.publicImage()))
              case Failure(e) =>
                ApiResponseUtil.toResponse(ErrorSecretNotAdded("Failed to create key pair.", JOptional.of(e)))
            }
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
    withBasicAuth {
      _ => {
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
  }

  /**
   * Check total balance of the wallet.
   */
  def getTotalBalance: Route = (post & path("getTotalBalance")) {
    withBasicAuth {
      _ => {
        entity(as[ReqGetTotalBalance]) { _ =>
          applyOnNodeView { sidechainNodeView =>
            try {
              val wallet = sidechainNodeView.getNodeWallet
              val addressList = wallet.secretsOfType(classOf[PrivateKeySecp256k1])
              if (addressList.isEmpty) {
                ApiResponseUtil.toResponse(RespGetBalance(BigInteger.ZERO))
              } else {

                val listOfAddressPropositions = addressList.asScala.map(s =>
                  s.publicImage().asInstanceOf[AddressProposition])
                var totalBalance = BigInteger.ZERO
                listOfAddressPropositions.foreach(address =>
                  totalBalance = totalBalance.add(sidechainNodeView.getNodeState.getBalance(address.address())))

                ApiResponseUtil.toResponse(RespGetBalance(totalBalance))
              }
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

  /**
   * get all balances of the wallet, return a list of pairs (address, balance).
   */
  def getAllBalances: Route = (post & path("getAllBalances")) {
    withBasicAuth {
      _ => {
        entity(as[ReqGetTotalBalance]) { _ =>
          applyOnNodeView { sidechainNodeView =>
            try {
              val wallet = sidechainNodeView.getNodeWallet
              val addressList = wallet.secretsOfType(classOf[PrivateKeySecp256k1])
              if (addressList.isEmpty) {
                ApiResponseUtil.toResponse(RespGetAllBalances(Seq().toList))
              } else {

                val addressPropositions = addressList.asScala.map(_.publicImage().asInstanceOf[AddressProposition])

                val accountBalances: List[AccountBalance] = addressPropositions.foldLeft(List.empty[AccountBalance]) {
                  (listToFill, addressProposition) =>
                    listToFill :+ AccountBalance(
                    address = addressProposition.address().toStringNoPrefix,
                      balance = sidechainNodeView.getNodeState.getBalance(addressProposition.address()))
                }

                ApiResponseUtil.toResponse(RespGetAllBalances(accountBalances))
              }
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
}

object AccountWalletRestScheme {
  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespGetBalance(balance: BigInteger) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class AccountBalance(address: String, balance: BigInteger)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespGetAllBalances(balances: List[AccountBalance]) extends SuccessResponse

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
