package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.WalletBaseErrorResponse.ErrorSecretNotAdded
import com.horizen.api.http.WalletBaseRestScheme.{ReqAllPropositions, RespAllPublicKeys, RespCreatePrivateKey25519, RespCreateVrfSecret}
import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.node._
import com.horizen.proposition.{Proposition, VrfPublicKey}
import com.horizen.secret.{PrivateKey25519Creator, VrfKeyGenerator}
import com.horizen.serialization.Views
import com.horizen.transaction.Transaction
import com.horizen.{AbstractSidechainNodeViewHolder, SidechainNodeViewBase, SidechainTypes}
import scorex.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

abstract class WalletBaseApiRoute[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  NH <: NodeHistoryBase[TX, H, PM],
  NS <: NodeStateBase,
  NW <: NodeWalletBase,
  NP <: NodeMemoryPoolBase[TX],
  NV <: SidechainNodeViewBase[TX, H, PM, NH, NS, NW, NP]](override val settings: RESTApiSettings,
                                   sidechainNodeViewHolderRef: ActorRef)(implicit val context: ActorRefFactory, override val ec: ExecutionContext, override val tag: ClassTag[NV])
  extends SidechainApiRoute[TX, H, PM, NH, NS, NW, NP, NV] {


  /**
   * Create new Vrf secret and return corresponding public key
   */
  def createVrfSecret: Route = (post & path("createVrfSecret")) {
    withNodeView { sidechainNodeView =>
      //replace to VRFKeyGenerator.generateNextSecret(wallet)
      val secret = VrfKeyGenerator.getInstance().generateNextSecret(sidechainNodeView.getNodeWallet)
      val public = secret.publicImage()

      val future = sidechainNodeViewHolderRef ? AbstractSidechainNodeViewHolder.ReceivableMessages.LocallyGeneratedSecret(secret)
      Await.result(future, timeout.duration).asInstanceOf[Try[Unit]] match {
        case Success(_) =>
          ApiResponseUtil.toResponse(RespCreateVrfSecret(public))
        case Failure(e) =>
          ApiResponseUtil.toResponse(ErrorSecretNotAdded("Failed to create Vrf key pair.", JOptional.of(e)))
      }
    }
  }

  /**
    * Create new secret and return corresponding address (public key)
    */
  def createPrivateKey25519: Route = (post & path("createPrivateKey25519")) {
    withNodeView { sidechainNodeView =>
      val wallet = sidechainNodeView.getNodeWallet
      val secret = PrivateKey25519Creator.getInstance().generateNextSecret(wallet)
      val future = sidechainNodeViewHolderRef ? AbstractSidechainNodeViewHolder.ReceivableMessages.LocallyGeneratedSecret(secret)
      Await.result(future, timeout.duration).asInstanceOf[Try[Unit]] match {
        case Success(_) =>
          ApiResponseUtil.toResponse(RespCreatePrivateKey25519(secret.publicImage()))
        case Failure(e) =>
          ApiResponseUtil.toResponse(ErrorSecretNotAdded("Failed to create key pair.", JOptional.of(e)))
      }
    }
  }

  /**
    * Returns the list of all wallet’s propositions (public keys). Filter propositions of the given type
    */
  def allPublicKeys: Route = (post & path("allPublicKeys")) {
    entity(as[ReqAllPropositions]) { body =>
      withNodeView { sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        val optPropType = body.proptype
        if (optPropType.isEmpty) {
          val listOfPropositions = wallet.allSecrets().asScala.map(s =>
            s.publicImage().asInstanceOf[SidechainTypes#SCP])
          ApiResponseUtil.toResponse(RespAllPublicKeys(listOfPropositions))
        } else {
          val clazz: java.lang.Class[_ <: SidechainTypes#SCS] = getClassBySecretClassName(optPropType.get)
          val listOfPropositions = wallet.secretsOfType(clazz).asScala.map(secret =>
            secret.publicImage().asInstanceOf[SidechainTypes#SCP])
          ApiResponseUtil.toResponse(RespAllPublicKeys(listOfPropositions))
        }
      }
    }
  }

  def getClassBySecretClassName(className: String): java.lang.Class[_ <: SidechainTypes#SCS] = {
    Try{Class.forName(className).asSubclass(classOf[SidechainTypes#SCS])}.
      getOrElse(Class.forName("com.horizen.secret." + className).asSubclass(classOf[SidechainTypes#SCS]))
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

}

object WalletBaseErrorResponse {

  case class ErrorSecretNotAdded(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0301"
  }

}