package com.horizen.api.http

import java.util.function.Consumer

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.horizen.SidechainTypes
import com.horizen.secret.{PrivateKey25519Creator, Secret}
import io.circe.Json
import scorex.core.settings.RESTApiSettings
import io.circe.generic.auto._
import scorex.core.transaction.box.proposition.PublicKey25519Proposition

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

case class SidechainWalletApiRoute(override val settings: RESTApiSettings,
                                   sidechainNodeViewHolderRef: ActorRef)(implicit val context: ActorRefFactory, override val ec : ExecutionContext)
      extends SidechainApiRoute {

  override val route : Route = (pathPrefix("wallet"))
            {getAllBoxes ~ getBalance  ~ createNewPublicKeyProposition ~ getPropositions}

  /**
    * Return all boxes, excluding those which ids are included in 'excludeBoxIds' list. Filter boxes of a given type
    */
  def getAllBoxes : Route = (post & path("allBoxes"))
  {
    case class GetBoxesRequest(boxTypeClass: Option[String], excludeBoxIds: Option[List[String]])

    entity(as[GetBoxesRequest]) { body =>
      withNodeView{ sidechainNodeView =>
        var optBoxTypeClass = body.boxTypeClass
        var wallet = sidechainNodeView.getNodeWallet
        var idsOfBoxesToExclude = body.excludeBoxIds.getOrElse(List()).map(strId => strId.getBytes)

        if(optBoxTypeClass.isEmpty){
          var closedBoxesJson = wallet.allBoxes(idsOfBoxesToExclude.asJava).asScala.map( box => box.toJson)
          SidechainApiResponse(Json.obj("boxes" -> Json.fromValues(closedBoxesJson)))
        }else{
          var clazz : java.lang.Class[_<:SidechainTypes#SCB] = Class.forName(optBoxTypeClass.get).asSubclass(classOf[SidechainTypes#SCB])
          var allClosedBoxesByType = wallet.boxesOfType(clazz, idsOfBoxesToExclude.asJava)
          var closedBoxesJson = allClosedBoxesByType.asScala.map(box => box.toJson)
          SidechainApiResponse(Json.obj("boxes" -> Json.fromValues(closedBoxesJson)))
        }
      }
    }
  }

  /**
    * Returns the balance for given box type, or all types of boxes
    */
  def getBalance : Route = (post & path("balance"))
  {
    case class GetBalanceByTypeRequest(boxType: Option[String])
    entity(as[GetBalanceByTypeRequest]) { body =>
      withNodeView{ sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        var optBoxType = body.boxType
        if(optBoxType.isEmpty){
          var sumOfBalances : Long = wallet.allBoxesBalance()
          SidechainApiResponse(Json.obj("balance" -> Json.fromLong(sumOfBalances)))

        }else{
          var clazz : java.lang.Class[_ <: SidechainTypes#SCB] = Class.forName(optBoxType.get).asSubclass(classOf[SidechainTypes#SCB])
          var balance = wallet.boxesBalance(clazz)
          SidechainApiResponse("balance" -> balance)
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

        val key = PrivateKey25519Creator.getInstance().generateSecretWithContext(wallet)
        if(wallet.addNewSecret(key)) {
          SidechainApiResponse(Json.obj("proposition" -> key.publicImage().toJson))
        } else
          SidechainApiResponse(
            SidechainApiErrorResponse(
              WalletApiGroupErrorCodes.SECRET_NOT_ADDED, "Failed to create key pair."))
      }
    }
  }

  /**
    * Returns the list of all wallet’s propositions (public keys). Filter propositions of the given type
    */
  def getPropositions : Route = (post & path("allPropositions"))
  {
    case class GetPublicKeysPropositionsByTypeRequest(proptype: Option[String])
    entity(as[GetPublicKeysPropositionsByTypeRequest]) { body =>
      withNodeView{ sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        var optPropType = body.proptype
        if(optPropType.isEmpty){
          SidechainApiResponse(Json.obj("propositions" -> Json.fromValues(wallet.allSecrets().asScala.map(s => s.publicImage().toJson))))
        }else{
          var clazz : java.lang.Class[_ <: SidechainTypes#SCS] = Class.forName(optPropType.get).asSubclass(classOf[SidechainTypes#SCS])
          var listOfPropositions = wallet.secretsOfType(clazz)
          var listOfAddresses : Seq[String] = Seq()
          listOfPropositions.forEach(new Consumer[Secret] {
            override def accept(t: Secret): Unit = {
              var proofOfKnowledgeProposition = t.publicImage()
              if(t.isInstanceOf[PublicKey25519Proposition])
                listOfAddresses.+(t.asInstanceOf[PublicKey25519Proposition].address)
            }
          })
          SidechainApiResponse("propositions" -> listOfAddresses)
        }
      }
    }
  }
}
