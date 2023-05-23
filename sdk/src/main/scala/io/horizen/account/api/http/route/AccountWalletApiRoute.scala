package io.horizen.account.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import io.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages
import io.horizen.SidechainTypes
import io.horizen.account.api.http.route.AccountWalletErrorResponse.ErrorCouldNotGetBalance
import io.horizen.account.api.http.route.AccountWalletRestScheme._
import io.horizen.account.block.{AccountBlock, AccountBlockHeader}
import io.horizen.account.chain.AccountFeePaymentsInfo
import io.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import io.horizen.api.http.JacksonSupport._
import io.horizen.api.http.route.WalletBaseApiRoute
import io.horizen.api.http.route.WalletBaseErrorResponse.ErrorSecretNotAdded
import io.horizen.api.http.route.WalletBaseRestScheme.{ReqCreateKey, RespCreatePrivateKey}
import io.horizen.api.http.{ApiResponseUtil, ErrorResponse, SuccessResponse}
import io.horizen.companion.SidechainSecretsCompanion
import io.horizen.json.Views
import io.horizen.node.NodeWalletBase
import io.horizen.utils.BytesUtils
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

  override val route: Route = pathPrefix(myPathPrefix) {
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
          withNodeView { _ =>
            val secretFuture = sidechainNodeViewHolderRef ? ReceivableMessages.GenerateSecret(PrivateKeySecp256k1Creator.getInstance)
            Await.result(secretFuture, timeout.duration).asInstanceOf[Try[PrivateKeySecp256k1]] match {
              case Success(secret: PrivateKeySecp256k1) =>
                ApiResponseUtil.toResponse(RespCreatePrivateKey(secret.publicImage()))
              case Failure(e) =>
                ApiResponseUtil.toResponse(ErrorSecretNotAdded("Failed to create secret.", JOptional.of(e)))
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
   private[horizen] case class RespGetBalance(balance: BigInteger) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class AccountBalance(address: String, balance: BigInteger)

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespGetAllBalances(balances: List[AccountBalance]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqGetBalance(address: String) {
    require(address.nonEmpty, "Empty address")
  }

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqGetTotalBalance() {
  }
}

object AccountWalletErrorResponse {
  case class ErrorCouldNotGetBalance(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0302"
  }
}
