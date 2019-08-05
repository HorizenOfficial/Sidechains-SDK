package com.horizen.api.http

import java.util
import java.util.function.Consumer

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.horizen.SidechainTypes
import com.horizen.box.BoxSerializer
import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.secret.Secret
import scorex.core.api.http.{ApiError, ApiResponse}
import scorex.core.settings.RESTApiSettings
import io.circe.generic.auto._
import scorex.core.transaction.box.proposition.PublicKey25519Proposition

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

case class SidechainWalletApiRoute(override val settings: RESTApiSettings,
                                   sidechainNodeViewHolderRef: ActorRef)(implicit val context: ActorRefFactory, override val ec : ExecutionContext)
      extends SidechainApiRoute {

  override val route : Route = (pathPrefix("wallet"))
            {getAllBoxes ~ getBoxesOfType ~ getBalance ~ getBalanceOfType ~ createNewPublicKeyProposition ~ getPublicKeyPropositions ~ getPublicKeyPropositionByType}


  private var boxCompanion : SidechainBoxesCompanion = new SidechainBoxesCompanion(new util.HashMap[java.lang.Byte, BoxSerializer[SidechainTypes#SCB]]())

  /**
    * Return all boxes, excluding those which ids are included in 'excludeBoxIds' list.
    */
  def getAllBoxes : Route = (post & path("getAllBoxes"))
  {
    case class GetBoxesRequest(excludeBoxIds: List[String] = List[String]())

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetBoxesRequest](body)match {
          case Success(req) =>
            var wallet = sidechainNodeView.getNodeWallet

            var idsOfBoxesToExclude = req.excludeBoxIds.map(strId => strId.getBytes)

            var allClosedBoxes = wallet.allBoxes(idsOfBoxesToExclude.asJava)
            var scalaClosedBoxesSerialized = allClosedBoxes.asScala.map(box => ("box", boxCompanion.toBytes(box)))

            ApiResponse("result" -> scalaClosedBoxesSerialized)

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
            var wallet = sidechainNodeView.getNodeWallet

            var idsOfBoxesToExclude = req.excludeBoxIds.map(strId => strId.getBytes)
            var clazz : java.lang.Class[_<:SidechainTypes#SCB] = Class.forName(req.boxTypeClass).asSubclass(classOf[SidechainTypes#SCB])

            var allClosedBoxesByType = wallet.boxesOfType(clazz, idsOfBoxesToExclude.asJava)
            var scalaClosedBoxesSerialized = allClosedBoxesByType.asScala.map(box => ("box", boxCompanion.toBytes(box)))

            ApiResponse("result" -> scalaClosedBoxesSerialized)

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
        var sumOfBalances : Long = wallet.allBoxesBalance()
        ApiResponse("result" -> ("globalBalance", sumOfBalances))
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
            ApiResponse("result" -> ("balance" -> balance))

          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  /**
    * Create new secret and return corresponding address (public key)
    */
  def createNewPublicKeyProposition : Route = (post & path("createNewPublicKeyProposition"))
  {
    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet

        ApiResponse("result" -> ("address", wallet.generateNewAddress()))
      }
    }
  }

  /**
    * Returns the list of all wallet’s addresses (public keys)
    */
  def getPublicKeyPropositions : Route = (post & path("getPublicKeyPropositions"))
  {
    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        var listOfPropositions = wallet.allSecrets()
        var listOfAddresses : Seq[String] = Seq()
        listOfPropositions.forEach(new Consumer[Secret] {
          override def accept(t: Secret): Unit = {
            var proofOfKnowledgeProposition = t.publicImage()
            if(t.isInstanceOf[PublicKey25519Proposition])
              listOfAddresses.+(t.asInstanceOf[PublicKey25519Proposition].address)
          }
        })
        ApiResponse("result" -> listOfAddresses)
      }
    }
  }

  /**
    * Returns the list of all wallet’s addresses (public keys) of the given type
    */
  def getPublicKeyPropositionByType : Route = (post & path("getPublicKeyPropositionByType"))
  {
    case class GetPublicKeysPropositionsByTypeRequest(proptype: String = "")
    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetPublicKeysPropositionsByTypeRequest](body)match {
          case Success(req) =>
            val wallet = sidechainNodeView.getNodeWallet
            var clazz : java.lang.Class[_ <: SidechainTypes#SCS] = Class.forName(req.proptype).asSubclass(classOf[SidechainTypes#SCS])
            var listOfPropositions = wallet.secretsOfType(clazz)
            var listOfAddresses : Seq[String] = Seq()
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
