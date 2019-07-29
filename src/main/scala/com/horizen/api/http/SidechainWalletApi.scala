package com.horizen.api.http

import java.util.function.Consumer

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.horizen.box.{Box, BoxSerializer}
import com.horizen.proposition.Proposition
import scorex.core.api.http.{ApiError, ApiResponse}
import scorex.core.settings.RESTApiSettings
import io.circe.generic.auto._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

case class SidechainWalletApi (override val settings: RESTApiSettings,
                               sidechainNodeViewHolderRef: ActorRef) (implicit val context: ActorRefFactory, override val ec : ExecutionContext)
      extends SidechainApiRoute {

  override val route : Route = (pathPrefix("wallet"))
            {getBalance ~ getBalanceOfType ~ createNewPublicKeyPropositions ~ getPublicKeyPropositionByType}

  /**
    * Returns global balance for all types of boxes
    */
  def getBalance : Route =
  {
    withNodeView{
      sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        var sumOfBalances : Long = wallet.allBoxesBalance()
        ApiResponse("result" -> sumOfBalances)
    }
  }

  /**
    * Returns the balance for given box type
    */
  def getBalanceOfType : Route =
  {
    case class GetBalanceByTypeRequest(boxType: String)
    ApiResponse.OK
  }

  def createNewPublicKeyPropositions : Route = ???

  def getPublicKeyPropositionByType : Route =
  {
    case class GetPublicKeysPropositionsByTypeRequest(proptype: String)
    ApiResponse.OK
  }

}
