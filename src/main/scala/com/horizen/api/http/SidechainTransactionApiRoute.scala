package com.horizen.api.http

import java.lang.{Byte, reflect}
import java.util.{Collections, Optional}
import java.util.function.Consumer
import java.{lang, util}

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.box.{Box, PublicKey25519NoncedBox, RegularBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.node.{NodeMemoryPool, NodeWallet, SidechainNodeView}
import com.horizen.proposition.{ProofOfKnowledgeProposition, Proposition, PublicKey25519Proposition, PublicKey25519PropositionSerializer}
import com.horizen.secret.{PrivateKey25519, Secret}
import com.horizen.{SidechainTypes, transaction}
import com.horizen.transaction._
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import scorex.core.api.http.{ApiError, ApiResponse}
import scorex.core.settings.RESTApiSettings
import io.circe.generic.auto._
import io.circe.syntax._
import javafx.util.Pair
import io.circe.Json
import scorex.util.ModifierId

import scorex.util.bytesToId

import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

case class SidechainTransactionApiRoute(override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef,
                                        sidechainTransactionActorRef: ActorRef)(implicit val context: ActorRefFactory, override val ec : ExecutionContext)
      extends SidechainApiRoute {

  override val route : Route = (pathPrefix("transaction"))
            {getMemoryPool ~ decodeRawTransaction ~ createRegularTransaction ~ createRegularTransactionSimplified ~ sendCoinsToAddress ~ sendRawTransaction}

  private var companion : SidechainTransactionsCompanion = new SidechainTransactionsCompanion(new util.HashMap[Byte, TransactionSerializer[SidechainTypes#SCBT]]())

  case class TransactionInput(boxId: String)
  case class TransactionOutput(publicKey: String, value: Long)
  case class CreateRegularTransactionRequest(transactionInputs: List[TransactionInput],
                                             transactionOutputs: List[TransactionOutput],
                                             format: Boolean = false)
  {
    require(transactionInputs.nonEmpty, "Empty inputs list")
    require(transactionOutputs.nonEmpty, "Empty outputs list")
  }

  case class CreateRegularTransactionSimplifiedRequest(transactionOutputs: List[TransactionOutput],
                                                       fee: Long,
                                                       format: Boolean = false){
    require(transactionOutputs.nonEmpty, "Empty outputs list")
    require(fee >= 0, "Negative fee. Fee must be >= 0")
  }

  case class SendCoinsToAddressesRequest(outputs: List[TransactionOutput], fee: Option[Long]){
    require(outputs.nonEmpty, "Empty outputs list")
    require(fee.getOrElse(0L) >= 0, "Negative fee. Fee must be >= 0")
  }

  /**
    * Returns an array of transaction ids if formatMemPool=false, otherwise a JSONObject for each transaction.
    */
  def getMemoryPool : Route = (post & path("getMemoryPool"))
  {
    case class GetMempoolRequest(format: Option[Boolean] = Some(true))

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetMempoolRequest](body)match {
          case Success(req) =>
            var unconfirmedTxs = sidechainNodeView.getNodeMemoryPool.allTransactions()
            if(req.format.getOrElse(true)){
              ApiResponse(
                "result" -> Json.obj(
                  "transactions" -> Json.fromValues(unconfirmedTxs.asScala.map(tx => tx.toJson))
                )
              )
            } else {
              ApiResponse(
                "result" -> Json.obj(
                  "transactions" -> Json.fromValues(unconfirmedTxs.asScala.map(tx => Json.fromString(tx.id.toString)))
                )
              )
            }
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  /**
    * Follows the same behaviour as the corresponding RPC call in zend: by default it will look for
    * transaction in memory pool. Additional parameters are:
    * -format: if true a JSON representation of transaction is returned, otherwise return the transaction serialized as
    * a hex string. If format is not specified, false behaviour is assumed as default;
    * -blockHash: If specified, will look for tx in the corresponding block
    * -txIndex: If specified will look for transaction in all blockchain blocks;
    *
    * All the possible behaviours are be:
    * 1) blockHash set -> Search in block referenced by blockHash (do not care about txIndex parameter)
    * 2) blockHash not set, txIndex = true -> Search in memory pool, if not found, search in the whole blockchain
    * 3) blockHash not set, txIndex = false -> Search in memory pool
    */
  def getRawTransaction : Route = (post & path("getRawTransaction"))
  {
    case class GetRawTransactionRequest(txId: String, blockHash: String = "", txIndex: Boolean = false, format: Boolean = false)

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetRawTransactionRequest](body)match {
          case Success(req) =>
            val memoryPool = sidechainNodeView.getNodeMemoryPool
            val history = sidechainNodeView.getNodeHistory

            def searchTransactionInMemoryPool(id : String) : Option[_ <: Transaction] = {
              var opt = memoryPool.getTransactionByid(id)
              if(opt.isPresent)
                //None
                Option(opt.get())
              else None
            }

            def searchTransactionInBlock(id : String, blockHash : String) : Option[_ <: Transaction] = {
              var opt = history.searchTransactionInsideSidechainBlock(id, blockHash)
              if(opt.isPresent)
                Option(opt.get())
              else None
            }

            def searchTransactionInBlockchain(id : String) : Option[_ <: Transaction] = {
              var opt = history.searchTransactionInsideBlockchain(id)
              if(opt.isPresent)
                Option(opt.get())
              else None
            }


            var txId = req.txId
            var format = req.format
            var blockHash = req.blockHash
            var txIndex = req.txIndex
            var transaction : Option[Transaction] = None
            var error : String = ???

            // Case --> blockHash not set, txIndex = true -> Search in memory pool, if not found, search in the whole blockchain
            if(blockHash.isEmpty && txIndex){
              // Search first in memory pool
              transaction = searchTransactionInMemoryPool(txId)

              // If not found search in the whole blockchain
              if(transaction.isEmpty)
                transaction = searchTransactionInBlockchain(txId)

              if(transaction.isEmpty)
                error = s"Transaction $txId not found in memory pool and blockchain"
            }
            // Case --> blockHash not set, txIndex = false -> Search in memory pool
            else if(blockHash.isEmpty && !txIndex){
              // Search in memory pool
              transaction = searchTransactionInMemoryPool(txId)

              if(transaction.isEmpty)
                error = s"Transaction $txId not found in memory pool"
            }

            // Case --> blockHash set -> Search in block referenced by blockHash (do not care about txIndex parameter)
            else if(!blockHash.isEmpty){
              transaction = searchTransactionInBlock(txId, blockHash)

              if(transaction.isEmpty)
                error = s"Transaction $txId not found in specified block"
            }

            transaction match {
              case Some(t) =>
                if(format){
                  //TO-DO JSON representation of transaction
                  ApiResponse("result" -> ("transaction", t.toJson.asString.get))
                }else{
                  ApiResponse("result"->companion.toBytes(t))
                }
              case None =>
                // TO-DO Change the errorCode
                ApiResponse("error" -> ("errorCode" -> 999999, error))
            }

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
            var bytes = BytesUtils.fromHexString(req.rawtxdata)
            var tryTX = companion.parseBytesTry(BytesUtils.fromHexString(req.rawtxdata))
            tryTX match{
              case Success(tx) =>
                //TO-DO JSON representation of transaction
                ApiResponse("result" -> Json.obj(("transaction", tx.toJson)))
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

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[CreateRegularTransactionRequest](body)match {
          case Success(req) =>
            val wallet = sidechainNodeView.getNodeWallet
            val inputBoxes = wallet.allBoxes().asScala
              .filter(box => req.transactionInputs.exists(p => p.boxId.contentEquals(BytesUtils.toHexString(box.id()))))

            if(inputBoxes.length < req.transactionInputs.size) {
              ApiResponse("error" -> ("errorCode" -> 999999, "errorDescription" -> s"Unable to find input(s)"))
            } else {
              var inSum: Long = 0
              var outSum: Long = 0

              val inputs : IndexedSeq[Pair[RegularBox, PrivateKey25519]] = inputBoxes.map(box =>
                {
                  var secret = wallet.secretByPublicKey(box.proposition())
                  var privateKey = secret.get().asInstanceOf[PrivateKey25519]
                  wallet.secretByPublicKey(box.proposition()).get().asInstanceOf[PrivateKey25519]
                  new Pair(
                    box.asInstanceOf[RegularBox],
                    privateKey)
                }
              ).toIndexedSeq

              val outputs : IndexedSeq[Pair[PublicKey25519Proposition, lang.Long]] = req.transactionOutputs.map(element =>
                new Pair(
                  PublicKey25519PropositionSerializer.getSerializer().parseBytes(BytesUtils.fromHexString(element.publicKey)),
                  new lang.Long(element.value))
              ).toIndexedSeq

              inputs.foreach(pair => inSum += pair.getKey.value())
              outputs.foreach(pair => outSum += pair.getValue)

              val fee : Long = inSum - outSum

              try {
                val regularTransaction = RegularTransaction.create(
                  JavaConverters.seqAsJavaList(inputs),
                  JavaConverters.seqAsJavaList(outputs),
                  fee, System.currentTimeMillis())

                if (req.format)
                  ApiResponse("result" -> Json.obj(("transaction", regularTransaction.toJson)))
                else
                  ApiResponse("result" -> Json.obj(("transactionHex",
                    Json.fromString(BytesUtils.toHexString(companion.toBytes(regularTransaction))))))
              } catch {
                case t : Throwable =>
                  // TO-DO Change the errorCode
                  ApiResponse("error" -> ("errorCode" -> 99999, "errorDescription" -> t.getMessage))
              }
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
  def createRegularTransactionSimplified : Route = (post & path("createRegularTransactionSimplified"))
  {

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[CreateRegularTransactionSimplifiedRequest](body)match {
          case Success(req) =>
            var outputList = req.transactionOutputs
            var fee = req.fee
            val wallet = sidechainNodeView.getNodeWallet

            try {
              var regularTransaction = createRegularTransactionSimplified_(outputList, fee, wallet, sidechainNodeView)

              if(req.format)
                ApiResponse("result" -> Json.obj(("transaction", regularTransaction.toJson)))
              else
                ApiResponse("result" -> Json.obj(("transactionHex",
                  Json.fromString(BytesUtils.toHexString(companion.toBytes(regularTransaction))))))
            } catch {
              case t : Throwable =>
                // TO-DO Change the errorCode
                ApiResponse("error" -> ("errorCode" -> 99999, "errorDescription" -> t.getMessage))
            }
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  // Note: method should return Try[RegularTransaction]
  private def createRegularTransactionSimplified_(
                                                  outputList: List[TransactionOutput], fee: Long, wallet : NodeWallet,
                                                  sidechainNodeView : SidechainNodeView) : RegularTransaction = {

    val memoryPool = sidechainNodeView.getNodeMemoryPool
    val boxIdsToExclude : ArrayBuffer[scala.Array[scala.Byte]] = ArrayBuffer[scala.Array[scala.Byte]]()

    memoryPool.getTransactionsSortedByFee(memoryPool.getSize).forEach(new Consumer[transaction.BoxTransaction[_ <: Proposition, _ <: Box[_ <: Proposition]]] {
      override def accept(t: transaction.BoxTransaction[_ <: Proposition, _ <: Box[_ <: Proposition]]): Unit = {
        t.boxIdsToOpen().forEach(new Consumer[ByteArrayWrapper] {
          override def accept(t: ByteArrayWrapper): Unit = {
            boxIdsToExclude += t.data
          }
        })
      }
    })

    var outputs: java.util.List[Pair[PublicKey25519Proposition, lang.Long]] = outputList.map(element =>
      new Pair(new PublicKey25519Proposition(BytesUtils.fromHexString(element.publicKey)), new lang.Long(element.value))).asJava

    var tx: RegularTransaction = null
    try {
      tx = RegularTransactionCreator.create(wallet, outputs, wallet.allSecrets().get(0).asInstanceOf[PrivateKey25519].publicImage(), fee, boxIdsToExclude.asJava)
    }
    catch {
      case e: Exception => log.error(s"RegularTransaction creaion error: ${e.getMessage}")
        throw e
    }
    tx // to do: see note above
  }

  /**
    * Create and sign a regular transaction, specifying outputs and fee. Then validate and send the transaction.
    * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
    */
  def sendCoinsToAddress : Route = (post & path("sendCoinsToAddress"))
  {
    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[SendCoinsToAddressesRequest](body)match {
          case Success(req) =>
            var outputList = req.outputs
            var fee = req.fee
            val wallet = sidechainNodeView.getNodeWallet

            try {
              var regularTransaction = createRegularTransactionSimplified_(outputList, fee.getOrElse(0L), wallet, sidechainNodeView)
              validateAndSendTransaction(regularTransaction)
            } catch {
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
        val barrier = Await.result(
          sidechainTransactionActorRef ? BroadcastTransaction(transaction),
          settings.timeout).asInstanceOf[Future[ModifierId]]
        onComplete(barrier){
          case Success(id) =>
            ApiResponse("result" -> Json.obj("transactionId" -> Json.fromString(id)))
          case Failure(exp) =>
            // TO-DO Change the errorCode
           ApiResponse("error" -> ("errorCode" -> 999999, "errorDescription" -> exp.getMessage))
        }
    }
  }

  /**
    * Validate and send a transaction, given its serialization as input.
    * Return error in case of invalid transaction or parsing error, otherwise return the id of the transaction.
    */
  def sendRawTransaction : Route = (post & path("sendRawTransaction"))
  {
    case class SendRawTransactionRequest(transactionHex: String)

    entity(as[String]) { body =>
      withNodeView { sidechainNodeView =>
        ApiInputParser.parseInput[SendRawTransactionRequest](body)match {
          case Success(req) =>
            var transactionBytes = BytesUtils.fromHexString(req.transactionHex)
            companion.parseBytesTry(transactionBytes) match {
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
