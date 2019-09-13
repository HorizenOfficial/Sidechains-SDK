package com.horizen.api.http

import java.util.function.Consumer

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.horizen.SidechainTypes
import com.horizen.secret.{PrivateKey25519Creator, Secret}
import io.circe.Json
import scorex.core.api.http.{ApiError, ApiResponse}
import scorex.core.settings.RESTApiSettings
import io.circe.generic.auto._
import scorex.core.transaction.box.proposition.PublicKey25519Proposition
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.LocallyGeneratedSecret
import akka.pattern.ask
import scorex.util.ModifierId

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class SidechainWalletApiRoute(override val settings: RESTApiSettings,
                                   sidechainNodeViewHolderRef: ActorRef)(implicit val context: ActorRefFactory, override val ec : ExecutionContext)
      extends SidechainApiRoute {

  override val route : Route = (pathPrefix("wallet"))
            {getAllBoxes ~ getBoxesOfType ~ getBalance ~ getBalanceOfType ~
              createSecret ~ getPublicKeys ~ getPublicKeyByType}

  /**
    * Return all boxes, excluding those which ids are included in 'excludeBoxIds' list.
    */
  def getAllBoxes : Route = (post & path("getAllBoxes"))
  {
    case class GetBoxesRequest(excludeBoxIds: Option[List[String]])

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetBoxesRequest](body) match {
          case Success(req) =>
            val wallet = sidechainNodeView.getNodeWallet
            val idsOfBoxesToExclude = req.excludeBoxIds.getOrElse(List()).map(strId => strId.getBytes)
            val closedBoxesJson = wallet.allBoxes(idsOfBoxesToExclude.asJava).asScala.map( box => box.toJson)

            ApiResponse("result" -> Json.obj("boxes" -> Json.fromValues(closedBoxesJson)))

          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  /**
    * Returns an array of JSONObjects each describing a box of a given type, excluding those which ids are included in 'excludeBoxIds' list.
    */
  def getBoxesOfType : Route = (post & path("getBoxesOfType"))
  {
    case class GetBoxesOfTypeRequest(boxTypeClass: String, excludeBoxIds: List[String] = List[String]())

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetBoxesOfTypeRequest](body)match {
          case Success(req) =>
            val wallet = sidechainNodeView.getNodeWallet

            val idsOfBoxesToExclude = req.excludeBoxIds.map(strId => strId.getBytes)
            val clazz: java.lang.Class[_<:SidechainTypes#SCB] = Class.forName(req.boxTypeClass).asSubclass(classOf[SidechainTypes#SCB])

            val allClosedBoxesByType = wallet.boxesOfType(clazz, idsOfBoxesToExclude.asJava)
            val closedBoxesJson = allClosedBoxesByType.asScala.map(box => box.toJson)

            ApiResponse("result" -> Json.obj("boxes" -> Json.fromValues(closedBoxesJson)))

          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  /**
    * Returns global balance for all types of boxes
    */
  def getBalance : Route = (post & path("getBalance"))
  {
    withNodeView{
      sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        val sumOfBalances: Long = wallet.allBoxesBalance()
        ApiResponse("result" -> Json.obj("globalBalance" -> Json.fromLong(sumOfBalances)))
    }
  }

  /**
    * Returns the balance for given box type
    */
  def getBalanceOfType : Route = (post & path("getBalanceOfType"))
  {
    case class GetBalanceByTypeRequest(boxType: String)
    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetBalanceByTypeRequest](body)match {
          case Success(req) =>
            val wallet = sidechainNodeView.getNodeWallet
            var clazz : java.lang.Class[_ <: SidechainTypes#SCB] = Class.forName(req.boxType).asSubclass(classOf[SidechainTypes#SCB])
            var balance = wallet.boxesBalance(clazz)
            ApiResponse("result" -> Json.obj(("balance" -> Json.fromLong(balance))))
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  /**
    * Create new secret and return corresponding address (public key)
    */
  def createSecret : Route = (post & path("createSecret"))
  {
    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        val secret = PrivateKey25519Creator.getInstance().generateNextSecret(wallet)

        val future = sidechainNodeViewHolderRef ? LocallyGeneratedSecret(secret)
        Await.result(future, timeout.duration).asInstanceOf[Try[Unit]] match {
          case Success(_) =>
            ApiResponse("result" -> Json.obj("proposition" -> secret.publicImage().toJson))
          case Failure(e) =>
            ApiResponse("error" -> ("errorCode" -> 999999, "errorDescription" -> s"Failed to create ne key pair: ${e.getMessage}."))
        }
      }
    }
  }

  /**
    * Returns the list of all wallet’s propositions (public keys)
    */
  def getPublicKeys : Route = (post & path("getPublicKeys"))
  {
    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        ApiResponse(
          "result" -> Json.obj(
            "propositions" -> Json.fromValues(wallet.allSecrets().asScala.map(s => s.publicImage().toJson))
            )
        )
      }
    }
  }

  /**
    * Returns the list of all wallet’s addresses (public keys) of the given type
    */
  def getPublicKeyByType : Route = (post & path("getPublicKeyByType"))
  {
    case class getPublicKeyByTypeRequest(proptype: String = "")
    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[getPublicKeyByTypeRequest](body)match {
          case Success(req) =>
            val wallet = sidechainNodeView.getNodeWallet
            val clazz: java.lang.Class[_ <: SidechainTypes#SCS] = Class.forName(req.proptype).asSubclass(classOf[SidechainTypes#SCS])
            val listOfPropositions = wallet.secretsOfType(clazz)
            val listOfAddresses: Seq[String] = Seq()
            listOfPropositions.forEach(new Consumer[Secret] {
              override def accept(t: Secret): Unit = {
                var proofOfKnowledgeProposition = t.publicImage()
                if(t.isInstanceOf[PublicKey25519Proposition])
                  listOfAddresses.+(t.asInstanceOf[PublicKey25519Proposition].address)
              }
            })
            ApiResponse("result" -> listOfAddresses)
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }
}
