package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.horizen.SidechainTypes
import com.horizen.secret.PrivateKey25519Creator
import scorex.core.settings.RESTApiSettings

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext}
import JacksonSupport._
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.LocallyGeneratedSecret
import com.horizen.api.http.SidechainWalletRestScheme._
import com.horizen.serialization.Views
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.api.http.SidechainWalletErrorResponse.ErrorSecretNotAdded
import com.horizen.box.Box
import com.horizen.proposition.Proposition

import scala.util.{Failure, Success, Try}

case class SidechainWalletApiRoute(override val settings: RESTApiSettings,
                                   sidechainNodeViewHolderRef: ActorRef)(implicit val context: ActorRefFactory, override val ec: ExecutionContext)
  extends SidechainApiRoute {

  override val route: Route = (pathPrefix("wallet")) {
    allBoxes ~ balance ~ createSecret ~ allPublicKeys
  }

  /**
    * Return all boxes, excluding those which ids are included in 'excludeBoxIds' list. Filter boxes of a given type
    */
  def allBoxes: Route = (post & path("allBoxes")) {
    entity(as[ReqAllBoxes]) { body =>
      withNodeView { sidechainNodeView =>
        var optBoxTypeClass = body.boxTypeClass
        var wallet = sidechainNodeView.getNodeWallet
        var idsOfBoxesToExclude = body.excludeBoxIds.getOrElse(List()).map(strId => strId.getBytes)
        if (optBoxTypeClass.isEmpty) {
          var closedBoxesJson = wallet.allBoxes(idsOfBoxesToExclude.asJava).asScala.toList
          ApiResponseUtil.toResponse(RespAllBoxes(closedBoxesJson))
        } else {
          var clazz: java.lang.Class[_ <: SidechainTypes#SCB] = Class.forName(optBoxTypeClass.get).asSubclass(classOf[SidechainTypes#SCB])
          var allClosedBoxesByType = wallet.boxesOfType(clazz, idsOfBoxesToExclude.asJava).asScala.toList
          ApiResponseUtil.toResponse(RespAllBoxes(allClosedBoxesByType))
        }
      }
    }
  }

  /**
    * Returns the balance for given box type, or all types of boxes
    */
  def balance: Route = (post & path("balance")) {
    entity(as[ReqBalance]) { body =>
      withNodeView { sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        var optBoxType = body.boxType
        if (optBoxType.isEmpty) {
          var sumOfBalances: Long = wallet.allBoxesBalance()
          ApiResponseUtil.toResponse(RespBalance(sumOfBalances))
        } else {
          var clazz: java.lang.Class[_ <: SidechainTypes#SCB] = Class.forName(optBoxType.get).asSubclass(classOf[SidechainTypes#SCB])
          var balance = wallet.boxesBalance(clazz)
          ApiResponseUtil.toResponse(RespBalance(balance))
        }
      }
    }
  }

  /**
    * Create new secret and return corresponding address (public key)
    */
  def createSecret: Route = (post & path("createSecret")) {
    withNodeView { sidechainNodeView =>
      val wallet = sidechainNodeView.getNodeWallet
      val secret = PrivateKey25519Creator.getInstance().generateNextSecret(wallet)
      val future = sidechainNodeViewHolderRef ? LocallyGeneratedSecret(secret)
      Await.result(future, timeout.duration).asInstanceOf[Try[Unit]] match {
        case Success(_) =>
          ApiResponseUtil.toResponse(RespCreateSecret(secret.publicImage()))
        case Failure(e) =>
          ApiResponseUtil.toResponse(ErrorSecretNotAdded("Failed to create key pair.", Some(e)))
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
        var optPropType = body.proptype
        if (optPropType.isEmpty) {
          var listOfPropositions = wallet.allSecrets().asScala.map(s =>
            s.publicImage().asInstanceOf[SidechainTypes#SCP])
          ApiResponseUtil.toResponse(RespAllPublicKeys(listOfPropositions))
        } else {
          var clazz: java.lang.Class[_ <: SidechainTypes#SCS] = Class.forName(optPropType.get).asSubclass(classOf[SidechainTypes#SCS])
          var listOfPropositions = wallet.secretsOfType(clazz).asScala.map(secret =>
            secret.publicImage().asInstanceOf[SidechainTypes#SCP])
          ApiResponseUtil.toResponse(RespAllPublicKeys(listOfPropositions))
        }
      }
    }
  }
}

object SidechainWalletRestScheme {

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqAllBoxes(boxTypeClass: Option[String], excludeBoxIds: Option[Seq[String]])

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllBoxes(boxes: List[Box[Proposition]]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqBalance(boxType: Option[String])

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespBalance(balance: Long) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCreateSecret(proposition: Proposition) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqAllPropositions(proptype: Option[String])

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllPublicKeys(propositions: Seq[Proposition]) extends SuccessResponse

}

object SidechainWalletErrorResponse {

  case class ErrorSecretNotAdded(description: String, exception: Option[Throwable]) extends ErrorResponse {
    override val code: String = "0301"
  }

}