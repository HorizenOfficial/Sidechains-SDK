package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages
import com.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.LocallyGeneratedSecret
import com.horizen.account.api.http.AccountWalletRestScheme.RespCreatePrivateKeySecp256k1
import com.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.SidechainWalletRestScheme.RespCreatePrivateKey
import com.horizen.api.http.WalletBaseErrorResponse.ErrorSecretNotAdded
import com.horizen.api.http.WalletBaseRestScheme.{ReqAllPropositions, ReqCreateKey, RespAllPublicKeys, RespCreateVrfSecret}
import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.chain.AbstractFeePaymentsInfo
import com.horizen.node._
import com.horizen.proposition.{Proposition, VrfPublicKey}
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator, VrfKeyGenerator, VrfSecretKey}
import com.horizen.serialization.Views
import com.horizen.transaction.Transaction
import com.horizen.{AbstractSidechainNodeViewHolder, SidechainNodeViewBase, SidechainTypes}
import sparkz.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

abstract class WalletBaseApiRoute[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  FPI <: AbstractFeePaymentsInfo,
  NH <: NodeHistoryBase[TX, H, PM, FPI],
  NS <: NodeStateBase,
  NW <: NodeWalletBase,
  NP <: NodeMemoryPoolBase[TX],
  NV <: SidechainNodeViewBase[TX, H, PM, FPI, NH, NS, NW, NP]](
                                   override val settings: RESTApiSettings,
                                   sidechainNodeViewHolderRef: ActorRef
                        )(implicit val context: ActorRefFactory, override val ec: ExecutionContext, override val tag: ClassTag[NV])
  extends SidechainApiRoute[TX, H, PM, FPI, NH, NS, NW, NP, NV] {



  /**
   * Create new Vrf secret and return corresponding public key
   */
  def createVrfSecret: Route = (post & path("createVrfSecret")) {
    withAuth {
      entity(as[ReqCreateKey]) { _ =>
        withNodeView { _ =>
          val secretFuture = sidechainNodeViewHolderRef ? ReceivableMessages.GenerateSecret(VrfKeyGenerator.getInstance)
          Await.result(secretFuture, timeout.duration).asInstanceOf[Try[VrfSecretKey]] match {
            case Success(secret: VrfSecretKey) =>
              val public = secret.publicImage()
              ApiResponseUtil.toResponse(RespCreateVrfSecret(public))
            case Failure(e) =>
              ApiResponseUtil.toResponse(ErrorSecretNotAdded("Failed to create secret.", JOptional.of(e)))
          }
        }
      }
    }
  }

  /**
   * Create new secret and return corresponding address (public key)
   */
  def createPrivateKey25519: Route = (post & path("createPrivateKey25519")) {
    withAuth {
      entity(as[ReqCreateKey]) { _ =>
        withNodeView { _ =>
          val secretFuture = sidechainNodeViewHolderRef ? ReceivableMessages.GenerateSecret(PrivateKey25519Creator.getInstance)
          Await.result(secretFuture, timeout.duration).asInstanceOf[Try[PrivateKey25519]] match {
            case Success(secret: PrivateKey25519) =>
              ApiResponseUtil.toResponse(RespCreatePrivateKey(secret.publicImage()))
            case Failure(e) =>
              ApiResponseUtil.toResponse(ErrorSecretNotAdded("Failed to create secret.", JOptional.of(e)))
          }
        }
      }
    }
  }

  /**
    * Returns the list of all wallet’s propositions (public keys). Filter propositions of the given type
    */
  def allPublicKeys: Route = (post & path("allPublicKeys")) {
    withAuth {
      entity(as[ReqAllPropositions]) { body =>
        withNodeView { sidechainNodeView =>
          val wallet = sidechainNodeView.getNodeWallet
          val optPropType = body.proptype
          if (optPropType.isEmpty) {
            val listOfPropositions = wallet.allSecrets().asScala.map(s =>
              s.publicImage().asInstanceOf[SidechainTypes#SCP])
            ApiResponseUtil.toResponse(RespAllPublicKeys(listOfPropositions))
          } else {

            getClassBySecretClassName(optPropType.get) match {
              case Failure(exception) => SidechainApiError(exception)
              case Success(clazz) =>
                val listOfPropositions = wallet.secretsOfType(clazz).asScala.map(secret =>
                  secret.publicImage().asInstanceOf[SidechainTypes#SCP])
                ApiResponseUtil.toResponse(RespAllPublicKeys(listOfPropositions))
            }
          }
        }
      }
    }
  }

  def getClassBySecretClassName(className: String): Try[java.lang.Class[_ <: SidechainTypes#SCS]] = {
    Try(Class.forName(className).asSubclass(classOf[SidechainTypes#SCS])) orElse
      Try(Class.forName("com.horizen.secret." + className).asSubclass(classOf[SidechainTypes#SCS]))
  }
}

object WalletBaseRestScheme {

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCreatePrivateKey25519(proposition: Proposition) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCreateVrfSecret(proposition: VrfPublicKey) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqAllPropositions(proptype: Option[String])

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllPublicKeys(propositions: Seq[Proposition]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  case class ReqCreateKey() {
  }
}

object WalletBaseErrorResponse {

  case class ErrorSecretNotAdded(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0301"
  }

}
