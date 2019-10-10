package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.horizen.SidechainTypes
import com.horizen.secret.PrivateKey25519Creator
import scorex.core.settings.RESTApiSettings

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import JacksonSupport._
import com.horizen.api.http.schema.SECRET_NOT_ADDED
import com.horizen.api.http.schema.WalletRestScheme._
import com.horizen.serialization.SerializationUtil

case class SidechainWalletApiRoute(override val settings: RESTApiSettings,
                                   sidechainNodeViewHolderRef: ActorRef)(implicit val context: ActorRefFactory, override val ec: ExecutionContext)
  extends SidechainApiRoute {

  override val route: Route = (pathPrefix("wallet")) {
    allBoxes ~ balance ~ newPublicKey ~ allPublicKeys
  }

  /**
    * Return all boxes, excluding those which ids are included in 'excludeBoxIds' list. Filter boxes of a given type
    */
  def allBoxes: Route = (post & path("allBoxes")) {

    entity(as[ReqAllBoxesPost]) { body =>
      withNodeView { sidechainNodeView =>
        var optBoxTypeClass = body.boxTypeClass
        var wallet = sidechainNodeView.getNodeWallet
        var idsOfBoxesToExclude = body.excludeBoxIds.getOrElse(List()).map(strId => strId.getBytes)

        if (optBoxTypeClass.isEmpty) {
          var closedBoxesJson = wallet.allBoxes(idsOfBoxesToExclude.asJava).asScala.toList
          SidechainApiResponse(
            SerializationUtil.serializeWithResult(
              RespAllBoxesPost(closedBoxesJson)
            )
          )
        } else {
          var clazz: java.lang.Class[_ <: SidechainTypes#SCB] = Class.forName(optBoxTypeClass.get).asSubclass(classOf[SidechainTypes#SCB])
          var allClosedBoxesByType = wallet.boxesOfType(clazz, idsOfBoxesToExclude.asJava).asScala.toList
          SidechainApiResponse(
            SerializationUtil.serializeWithResult(
              RespAllBoxesPost(allClosedBoxesByType)
            )
          )
        }
      }
    }
  }

  /**
    * Returns the balance for given box type, or all types of boxes
    */
  def balance: Route = (post & path("balance")) {

    entity(as[ReqBalancePost]) { body =>
      withNodeView { sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        var optBoxType = body.boxType
        if (optBoxType.isEmpty) {
          var sumOfBalances: Long = wallet.allBoxesBalance()
          SidechainApiResponse(
            SerializationUtil.serializeWithResult(
              RespBalancePost(sumOfBalances)
            )
          )
        } else {
          var clazz: java.lang.Class[_ <: SidechainTypes#SCB] = Class.forName(optBoxType.get).asSubclass(classOf[SidechainTypes#SCB])
          var balance = wallet.boxesBalance(clazz)
          SidechainApiResponse(
            SerializationUtil.serializeWithResult(
              RespBalancePost(balance)
            )
          )
        }

      }
    }
  }

  /**
    * Create new secret and return corresponding address (public key)
    */
  def newPublicKey: Route = (post & path("newPublicKey")) {
    withNodeView { sidechainNodeView =>
      val wallet = sidechainNodeView.getNodeWallet

      val key = PrivateKey25519Creator.getInstance().generateSecretWithContext(wallet)
      if (wallet.addNewSecret(key)) {
        SidechainApiResponse(
          SerializationUtil.serializeWithResult(
            RespCreateNewPublicKeyPost(key.publicImage())
          )
        )
      } else
        SidechainApiResponse(
          SerializationUtil.serializeErrorWithResult(
            SECRET_NOT_ADDED().apiCode, "Failed to create key pair.", "")
        )
    }
  }

  /**
    * Returns the list of all wallet’s propositions (public keys). Filter propositions of the given type
    */
  def allPublicKeys: Route = (post & path("allPublicKeys")) {
    entity(as[ReqAllPropositionsPost]) { body =>
      withNodeView { sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        var optPropType = body.proptype
        if (optPropType.isEmpty) {
          var listOfPropositions = wallet.allSecrets().asScala.map(s =>
            s.publicImage().asInstanceOf[SidechainTypes#SCP])
          SidechainApiResponse(
            SerializationUtil.serializeWithResult(
              RespAllPublicKeysPost(listOfPropositions)
            )
          )
        } else {
          var clazz: java.lang.Class[_ <: SidechainTypes#SCS] = Class.forName(optPropType.get).asSubclass(classOf[SidechainTypes#SCS])
          var listOfPropositions = wallet.secretsOfType(clazz).asScala.map(secret =>
            secret.publicImage().asInstanceOf[SidechainTypes#SCP])
          SidechainApiResponse(
            SerializationUtil.serializeWithResult(
              RespAllPublicKeysPost(listOfPropositions)
            )
          )
        }
      }
    }
  }
}
