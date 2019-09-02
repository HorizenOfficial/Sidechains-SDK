package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.horizen.api.http.dto.{POCDTORequest, POCDTOResponse}
import scorex.core.api.http.POCResponse
import scorex.core.settings.RESTApiSettings
import JacksonSupport._
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.{JsonSerializer, ObjectMapper}
import com.horizen.{SidechainSettings, SidechainTypes}
import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.box.{NoncedBox, RegularBox}
import com.horizen.params.MainNetParams
import com.horizen.proposition.{Proposition, PublicKey25519Proposition}
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator}
import com.horizen.transaction.{RegularTransaction, SidechainTransaction, TransactionSerializer}
import com.horizen.utils.BytesUtils

import scala.concurrent.ExecutionContext
import scala.io.Source
import scala.util.Try
import java.lang.{Long => JLong}
import java.util.{ArrayList => JArrayList}

import com.horizen.companion.SidechainTransactionsCompanion
import javafx.util.{Pair => JPair}
import java.util.{HashMap => JHashMap, ArrayList => JArrayList}
import java.lang.{Byte => JByte, Long => JLong}

case class POCApi(sc_settings : SidechainSettings,  val settings: RESTApiSettings,
                  sidechainNodeViewHolderRef: ActorRef)(implicit val context: ActorRefFactory, override val ec : ExecutionContext)
  extends SidechainApiRoute {

  override val route : Route = (pathPrefix("poc")) {pocRoute}

  def pocRoute : Route = (post & path("resource")) {
    entity(as[POCDTORequest]) { body =>
      withNodeView { sidechainNodeView =>

        val secretKey = PrivateKey25519Creator.getInstance().generateSecret("genesis_seed%d".format(123).getBytes)
        val targetSecretKey1 = PrivateKey25519Creator.getInstance().generateSecret("target1".getBytes)
        val targetSecretKey2 = PrivateKey25519Creator.getInstance().generateSecret("target2".getBytes)
        val fee = 2
        val timestamp = 1547798549470L
        val from = new JArrayList[JPair[RegularBox, PrivateKey25519]]
        val to = new JArrayList[JPair[PublicKey25519Proposition, JLong]]
        val creator = PrivateKey25519Creator.getInstance
        from.add(new JPair[RegularBox, PrivateKey25519](new RegularBox(secretKey.publicImage, 1, 30000L), secretKey))
        to.add(new JPair[PublicKey25519Proposition, JLong](targetSecretKey1.publicImage, 10000L))
        to.add(new JPair[PublicKey25519Proposition, JLong](targetSecretKey2.publicImage, 20000L))
        val transaction = RegularTransaction.create(from, to, fee, timestamp)

        println(body.body)
        var resp = new POCDTOResponse("the response")
        var js = JsonUtil.toJson(transaction)
        println(js)
        POCResponse(js)
      }
    }
  }

}
