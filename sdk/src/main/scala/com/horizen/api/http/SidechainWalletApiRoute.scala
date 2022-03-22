package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.SidechainNodeViewHolder.ReceivableMessages
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.LocallyGeneratedSecret
import com.horizen.SidechainTypes
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.SidechainWalletErrorResponse.ErrorSecretNotAdded
import com.horizen.api.http.SidechainWalletRestScheme._
import com.horizen.box.Box
import com.horizen.proposition.{Proposition, VrfPublicKey}
import com.horizen.secret.{PrivateKey25519Creator, VrfKeyGenerator}
import com.horizen.serialization.Views
import com.horizen.utils.BytesUtils
import scorex.core.settings.RESTApiSettings

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}
import java.util.{Optional => JOptional}

case class SidechainWalletApiRoute(override val settings: RESTApiSettings,
                                   sidechainNodeViewHolderRef: ActorRef)(implicit val context: ActorRefFactory, override val ec: ExecutionContext)
  extends SidechainApiRoute {

  override val route: Route = pathPrefix("wallet") {
    allBoxes ~ coinsBalance ~ balanceOfType ~ createPrivateKey25519 ~ createVrfSecret ~ allPublicKeys
  }

  /**
    * Return all boxes, excluding those which ids are included in 'excludeBoxIds' list. Filter boxes of a given type
    */
  def allBoxes: Route = (post & path("allBoxes")) {
    entity(as[ReqAllBoxes]) { body =>
      withNodeView { sidechainNodeView =>
        val optBoxTypeClass = body.boxTypeClass
        val wallet = sidechainNodeView.getNodeWallet
        val idsOfBoxesToExclude = body.excludeBoxIds.getOrElse(List()).map(idHex => BytesUtils.fromHexString(idHex))
        if (optBoxTypeClass.isEmpty) {
          val closedBoxesJson = wallet.allBoxes(idsOfBoxesToExclude.asJava).asScala.toList
          ApiResponseUtil.toResponse(RespAllBoxes(closedBoxesJson))
        } else {
          val clazz: java.lang.Class[_ <: SidechainTypes#SCB] = getClassByBoxClassName(optBoxTypeClass.get)
          val allClosedBoxesByType = wallet.boxesOfType(clazz, idsOfBoxesToExclude.asJava).asScala.toList
          ApiResponseUtil.toResponse(RespAllBoxes(allClosedBoxesByType))
        }
      }
    }
  }

  /**
    * Returns the balance of all types of coins boxes
    */
  def coinsBalance: Route = (post & path("coinsBalance")) {
    withNodeView { sidechainNodeView =>
      val wallet = sidechainNodeView.getNodeWallet
      val sumOfBalances: Long = wallet.allCoinsBoxesBalance()
      ApiResponseUtil.toResponse(RespBalance(sumOfBalances))
    }
  }

  /**
    * Returns the balance for given box type
    */
  def balanceOfType: Route = (post & path("balanceOfType")) {
    entity(as[ReqBalance]) { body =>
      withNodeView { sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        val clazz: java.lang.Class[_ <: SidechainTypes#SCB] = getClassByBoxClassName(body.boxType)
        val balance = wallet.boxesBalance(clazz)
        ApiResponseUtil.toResponse(RespBalance(balance))
      }
    }
  }

  /**
   * Create new Vrf secret and return corresponding public key
   */
  def createVrfSecret: Route = (post & path("createVrfSecret")) {
    withNodeView { sidechainNodeView =>
      //replace to VRFKeyGenerator.generateNextSecret(wallet)
      val secret = VrfKeyGenerator.getInstance().generateNextSecret(sidechainNodeView.getNodeWallet)
      val public = secret.publicImage()

      val future = sidechainNodeViewHolderRef ? ReceivableMessages.LocallyGeneratedSecret(secret)
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
      val future = sidechainNodeViewHolderRef ? LocallyGeneratedSecret(secret)
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

  def getClassByBoxClassName(className: String): java.lang.Class[_ <: SidechainTypes#SCB] = {
    Try{Class.forName(className).asSubclass(classOf[SidechainTypes#SCB])}.
      getOrElse(Class.forName("com.horizen.box." + className).asSubclass(classOf[SidechainTypes#SCB]))
  }
}

object SidechainWalletRestScheme {

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

  case class ErrorSecretNotAdded(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0301"
  }

}