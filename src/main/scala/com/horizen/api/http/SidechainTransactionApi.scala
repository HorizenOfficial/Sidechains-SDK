package com.horizen.api.http

import java.lang.{Byte, reflect}
import java.util.Collections
import java.util.function.Consumer
import java.{lang, util}

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.horizen.api.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.api.{ActorRegistry, SidechainTransactionActor}
import com.horizen.box.{Box, PublicKey25519NoncedBox, RegularBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.node.{NodeMemoryPool, NodeWallet}
import com.horizen.proposition.{ProofOfKnowledgeProposition, Proposition, PublicKey25519Proposition, PublicKey25519PropositionSerializer}
import com.horizen.secret.{PrivateKey25519, Secret}
import com.horizen.transaction
import com.horizen.transaction.{RegularTransaction, RegularTransactionSerializer, Transaction, TransactionSerializer}
import com.horizen.utils.ByteArrayWrapper
import scorex.core.api.http.{ApiError, ApiResponse}
import scorex.core.settings.RESTApiSettings
import io.circe.generic.auto._
import io.circe.{Encoder, Json, JsonObject}
import io.circe.syntax._
import javafx.util.Pair
import scorex.core.transaction.BoxTransaction

import scala.collection.JavaConverters
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Awaitable, Future}
import scala.util.{Failure, Success, Try}

case class SidechainTransactionApi (override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef,
                                    sidechainExtendedActorRegistry : ActorRegistry) (implicit val context: ActorRefFactory)
      extends SidechainApiRoute {

  override val route : Route = (pathPrefix("transaction"))
            {getMemoryPool ~ getTransaction ~ decodeRawTransaction ~ createRegularTransaction ~ sendCoinsToAddress ~ sendRawTransaction}

  /**
    * Returns an array of tx ids if formatMemPool=false, otherwise a JSONObject for each tx.
    */
  def getMemoryPool : Route = (post & path("getMemoryPool"))
  {
    case class GetMempoolRequest(formatMemPool: Boolean = false)

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetMempoolRequest](body)match {
          case Success(req) =>
            var formatMemPool = req.formatMemPool
            var unconfirmedTxs = sidechainNodeView.getNodeMemoryPool.getMemoryPool()
            if(formatMemPool){
              var keyArray = new Array[String](unconfirmedTxs.size())
              unconfirmedTxs.keySet().toArray(keyArray)
              ApiResponse("result" -> keyArray.asJson)
            }
             else
              {
                var valuesArray = new Array[BoxTransaction[_ <: Proposition, _ <: Box[_ <: Proposition]]](unconfirmedTxs.size())
                ApiResponse("result" -> valuesArray.map(_.bytes))
              }
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  /**
    * Return in-wallet tx by its id. Return error if not found
    */
  def getTransaction : Route = (post & path("getTransaction"))
  {
    case class GetTransactionRequest(txid: String, format: Boolean = false)

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetTransactionRequest](body)match {
          case Success(req) =>
            var txid = req.txid
            var format = req.format
            var option_transaction = sidechainNodeView.getNodeWallet.getTransactionById(txid)
            if(option_transaction.isPresent){
              var tx = option_transaction.get()
                if(format)
                  ApiResponse("result" -> "")
                else
                  ApiResponse("error" -> ("errorCode" -> StatusCodes.BadRequest.intValue, "errorDescription" -> s"No transaction found for id: $txid"))
            }
            else
              ApiResponse("error" -> ("errorCode" -> StatusCodes.BadRequest.intValue, "errorDescription" -> s"No transaction found for id: $txid"))
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  /**
    * Follows the same behaviour as the corresponding RPC call in zend: by default it will look for
    * tx in mempool. Additional parameters are:
    * -format: if true a JSON representation of tx is returned, otherwise return the tx serialized as
    * a hex string. If format is not specified, false behaviour is assumed as default;
    * -blockhash: If specified, will look for tx in the corresponding block
    * -txindex: If specified will look for tx in all blockchain blocks;
    *
    * All the possible behaviours are be:
    * 1) blockhash set -> Search in block referenced by blockhash
    * 2) blockhash not set, txindex = true -> Search in mempool, if not found, search in the whole blockchain
    * 3) blockhash not set, txindex = false -> Search in mempool
    */
  def getRawTransaction : Route = (post & path("getRawTransaction"))
  {
    case class GetRawTransactionRequest(txid: String, blockhash: String = "", txindex: Boolean = false, format: Boolean = false)

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetRawTransactionRequest](body)match {
          case Success(req) =>
            var txid = req.txid
            var format = req.format
            var blockHash = req.blockhash
            var txindex = req.txindex
            //TO-DO in Java interfaces
            ApiResponse.OK
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  /**
    * Return a JSON representation of a transaction given its byte serialization.
    */
  def decodeRawTransaction : Route = (post & path("decodeRawTransaction"))
  {
    case class DecodeRawTransactionRequest(rawtxdata: String)

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[DecodeRawTransactionRequest](body)match {
          case Success(req) =>
            var tryTX = new SidechainTransactionsCompanion(new util.HashMap[Byte, TransactionSerializer[_<:Transaction]](0))
              .parseBytes(req.rawtxdata.getBytes)
            tryTX match{
              case Success(tx) =>
                //TO-DO how to encode in JSON?
                ApiResponse.OK
              case Failure(exp) =>
                // TO-DO Change the errorCode
                ApiResponse("error" -> ("errorCode" -> 99999, "errorDescription" -> exp.getMessage))
            }
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  /**
    * Create and sign a regular transaction, specifying inputs and outputs.
    * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
    */
  def createRegularTransaction : Route = (post & path("createRegularTransaction"))
  {
    case class CreateRegularTransactionRequest(transactionInputs: List[String], transactionOutputs: List[(String, Long)], format: Boolean = false){
      require(transactionInputs.nonEmpty, "Empty inputs list")
      require(transactionOutputs.nonEmpty, "Empty outputs list")
    }

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[CreateRegularTransactionRequest](body)match {
          case Success(req) =>
            var inputStrs = req.transactionInputs
            val wallet = sidechainNodeView.getNodeWallet
            val boxesIterator = wallet.boxesOfType(scala.Predef.classOf[PublicKey25519NoncedBox[PublicKey25519Proposition]]).iterator()
            val seqOfBoxes = JavaConverters.asScalaIteratorConverter(boxesIterator).asScala.toSeq
            val tempBoxes : IndexedSeq[PublicKey25519NoncedBox[PublicKey25519Proposition]] =
              seqOfBoxes.map(box => box.asInstanceOf[PublicKey25519NoncedBox[PublicKey25519Proposition]])
                .filter(box => inputStrs.contains(encoder.encode(box.id()))).toIndexedSeq

            if(tempBoxes.length < inputStrs.size){
              val missingBoxes = inputStrs.diff(tempBoxes.map(box=>encoder.encode(box.id)).toList)
              var boxIdsToPrint = ""
              missingBoxes.foreach(id => boxIdsToPrint += id + "")
              // TO-DO Change the errorCode
              ApiResponse("error" -> ("errorCode" -> 999999, "errorDescription" -> s"Unable to find input(s) with id: $missingBoxes "))
            }
            else{
              var inSum: Long = 0
              var outSum: Long = 0

              val inputs : IndexedSeq[Pair[RegularBox, PrivateKey25519]] = tempBoxes.map(box =>
                {
                  var secret = wallet.secretByPublicKey(box.proposition())
                  var privateKey = secret.get().asInstanceOf[PrivateKey25519]
                  wallet.secretByPublicKey(box.proposition()).get().asInstanceOf[PrivateKey25519]
                  new Pair(
                    new RegularBox(privateKey.publicImage, box.nonce(), box.value()),
                    privateKey)
                }
              ).toIndexedSeq

              val outputs : IndexedSeq[Pair[PublicKey25519Proposition, lang.Long]] = req.transactionOutputs.map(element =>
                new Pair(
                  PublicKey25519PropositionSerializer.getSerializer().parseBytes(element._1.getBytes()).get,
                  new lang.Long(element._2))
              ).toIndexedSeq

              inputs.foreach(pair => inSum += pair.getKey.value())
              outputs.foreach(pair => outSum += pair.getValue)

              var fee : Long = inSum - outSum

              var regularTransaction = RegularTransaction.create(
                JavaConverters.seqAsJavaList(inputs),
                JavaConverters.seqAsJavaList(outputs),
                fee, System.currentTimeMillis())

              // TO-DO
              var jsonRegularTransaction : Json = ("regularTransaction" -> "value...").asJson

              if(req.format)
                ApiResponse("result" -> jsonRegularTransaction)
              else
                ApiResponse("result" -> RegularTransactionSerializer.getSerializer.toBytes(regularTransaction))
            }
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  /**
    * Create and sign a regular transaction, specifying outputs and fee.
    * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
    */
  def createRegularTransactionSimplified : Route = (post & path("createRegularTransaction"))
  {
    case class CreateRegularTransactionSimplifiedRequest(outputs: List[(String, Long)], fee: Long, format: Boolean = false){
      require(outputs.nonEmpty, "Empty outputs list")
      require(fee >= 0, "Negative fee. Fee must be >= 0")
    }

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[CreateRegularTransactionSimplifiedRequest](body)match {
          case Success(req) =>
            var outputList = req.outputs
            var fee = req.fee
            val wallet = sidechainNodeView.getNodeWallet
            val memoryPool = sidechainNodeView.getNodeMemoryPool

            try {
              var regularTransaction = createRegularTransactionSimplified(outputList, fee, wallet, memoryPool)

              // TO-DO
              var jsonRegularTransaction : Json = ("regularTransaction" -> "value...").asJson

              if(req.format)
                ApiResponse("result" -> jsonRegularTransaction)
              else
                ApiResponse("result" -> RegularTransactionSerializer.getSerializer.toBytes(regularTransaction))
            }catch {
              case t : Throwable =>
                // TO-DO Change the errorCode
                ApiResponse("error" -> ("errorCode" -> 99999, "errorDescription" -> t.getMessage))
            }
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  private def createRegularTransactionSimplified(outputList: List[(String, Long)], fee: Long, wallet : NodeWallet, memoryPool : NodeMemoryPool) : RegularTransaction = {

    val boxIdsToExclude : ArrayBuffer[scala.Array[scala.Byte]] = ArrayBuffer[scala.Array[scala.Byte]]()

    memoryPool.getMemoryPoolSortedByFee(memoryPool.getMemoryPoolSize).forEach(new Consumer[transaction.BoxTransaction[_ <: Proposition, _ <: Box[_ <: Proposition]]] {
      override def accept(t: transaction.BoxTransaction[_ <: Proposition, _ <: Box[_ <: Proposition]]): Unit = {
        t.boxIdsToOpen().forEach(new Consumer[ByteArrayWrapper] {
          override def accept(t: ByteArrayWrapper): Unit = {
            boxIdsToExclude += t.data
          }
        })
      }
    })

    val boxes = wallet.boxesOfType(
      scala.Predef.classOf[PublicKey25519NoncedBox[PublicKey25519Proposition]],
      JavaConverters.bufferAsJavaList(boxIdsToExclude))

    val inputs : IndexedSeq[Pair[RegularBox, PrivateKey25519]] = IndexedSeq[Pair[RegularBox, PrivateKey25519]]()

    boxes.forEach(new Consumer[Box[_ <: Proposition]] {
      override def accept(t: Box[_ <: Proposition]): Unit = {
        var box = t.asInstanceOf[PublicKey25519NoncedBox[PublicKey25519Proposition]]
        var secret = wallet.secretByPublicKey(box.proposition())
        var privateKey = secret.get().asInstanceOf[PrivateKey25519]
        wallet.secretByPublicKey(box.proposition()).get().asInstanceOf[PrivateKey25519]
        var pair = new Pair(
          new RegularBox(privateKey.publicImage, box.nonce(), box.value()),
          privateKey)
        inputs.+:(pair)
      }
    })

    val outputs : IndexedSeq[Pair[PublicKey25519Proposition, lang.Long]] = outputList.map(element =>
      new Pair(PublicKey25519PropositionSerializer.getSerializer().parseBytes(element._1.getBytes()).get, new lang.Long(element._2))).toIndexedSeq

    RegularTransaction.create(
      JavaConverters.seqAsJavaList(inputs),
      JavaConverters.seqAsJavaList(outputs),
      fee, System.currentTimeMillis())
  }

  /**
    * Create and sign a regular transaction, specifying outputs and fee. Then validate and send the transaction.
    * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
    */
  def sendCoinsToAddress : Route = (post & path("sendCoinsToAddress"))
  {
    case class SendCoinsToAddressesRequest(outputs: List[(String, Long)], fee: Long){
      require(outputs.nonEmpty, "Empty outputs list")
      require(fee >= 0, "Negative fee. Fee must be >= 0")
    }

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[SendCoinsToAddressesRequest](body)match {
          case Success(req) =>
            var outputList = req.outputs
            var fee = req.fee
            val wallet = sidechainNodeView.getNodeWallet
            val memoryPool = sidechainNodeView.getNodeMemoryPool

            try {
              var regularTransaction = createRegularTransactionSimplified(outputList, fee, wallet, memoryPool)
              validateAndSendTransaction(regularTransaction)
            }catch {
              case t : Throwable =>
                // TO-DO Change the errorCode
                ApiResponse("error" -> ("errorCode" -> 99999, "errorDescription" -> t.getMessage))
            }
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  private def validateAndSendTransaction(transaction : Transaction) = {
    withNodeView{
      sidechainNodeView =>
        //TO-DO
        sidechainExtendedActorRegistry.retrieveActorRef(SidechainTransactionActor.getClass.getCanonicalName) match {
          case Some(actorRef) =>
            val barrier = Await.result(
              actorRef ? BroadcastTransaction(transaction),
              settings.timeout).asInstanceOf[Future[Unit]]
            onComplete(barrier){
              case Success(result) =>
                ApiResponse("result" -> ("transactionId" -> transaction.id()))
              case Failure(exp) =>
                // TO-DO Change the errorCode
                ApiResponse("error" -> ("errorCode" -> 999999, "errorDescription" -> exp.getMessage))
            }
          case None =>
            // TO-DO Change the errorCode
            ApiResponse("error" -> ("errorCode" -> 999999, "errorDescription" -> "No extended actor found"))
        }
    }
  }

  /**
    * Validate and send a transaction, given its serialization as input.
    * Return error in case of invalid transaction or parsing error, otherwise return the id of the transaction.
    */
  def sendRawTransaction : Route = (post & path("sendRawTransaction"))
  {
    case class SendRawTransactionRequest(rawTransaction: String)

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[SendRawTransactionRequest](body)match {
          case Success(req) =>
            var transactionBytes = req.rawTransaction
            new SidechainTransactionsCompanion(new util.HashMap[Byte, TransactionSerializer[_ <: Transaction]]())
                .parseBytes(transactionBytes.getBytes) match {
              case Success(transaction) =>
                validateAndSendTransaction(transaction)
              case Failure(exception) =>
                // TO-DO Change the errorCode
                ApiResponse("error" -> ("errorCode" -> 999999, "errorDescription" -> exception.getMessage))
            }
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

}
