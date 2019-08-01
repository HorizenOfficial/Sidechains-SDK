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
import com.horizen.transaction.{RegularTransaction, RegularTransactionSerializer, Transaction, TransactionSerializer}
import com.horizen.utils.ByteArrayWrapper
import scorex.core.api.http.{ApiError, ApiResponse}
import scorex.core.settings.RESTApiSettings
import io.circe.generic.auto._
import io.circe.syntax._
import javafx.util.Pair

import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

case class SidechainTransactionApiRoute(override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef,
                                        sidechainTransactionActorRef: ActorRef)(implicit val context: ActorRefFactory, override val ec : ExecutionContext)
      extends SidechainApiRoute {

  override val route : Route = (pathPrefix("transaction"))
            {getMemoryPool ~ decodeRawTransaction ~ createRegularTransaction ~ sendCoinsToAddress ~ sendRawTransaction}

  private var companion : SidechainTransactionsCompanion = new SidechainTransactionsCompanion(new util.HashMap[Byte, TransactionSerializer[SidechainTypes#SCBT]]())
  /**
    * Returns an array of transaction ids if formatMemPool=false, otherwise a JSONObject for each transaction.
    */
  def getMemoryPool : Route = (post & path("getMemoryPool"))
  {
    case class GetMempoolRequest(formatMemPool: Boolean = false)

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetMempoolRequest](body)match {
          case Success(req) =>
            var formatMemPool = req.formatMemPool
            var unconfirmedTxs = sidechainNodeView.getNodeMemoryPool.allTransactions()
            if(formatMemPool){
              ApiResponse("result" -> unconfirmedTxs.asScala.map(tx => ("transactionId", tx.id.toString)))
            }
             else
              {
                var valuesArray : Array[Array[Byte]] = Array[Array[Byte]]()
                unconfirmedTxs.forEach(new Consumer[transaction.BoxTransaction[_ <: Proposition, _ <: Box[_ <: Proposition]]] {
                  override def accept(t: transaction.BoxTransaction[_ <: Proposition, _ <: Box[_ <: Proposition]]): Unit = {
                    valuesArray.+:(companion.toBytes(t))
                  }
              })
                ApiResponse("result" -> valuesArray.map(tx => ("transaction", tx)))
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
                  ApiResponse("result" -> ("transaction", t.asJson))
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
            var tryTX = companion.parseBytesTry(req.rawtxdata.getBytes)
            tryTX match{
              case Success(tx) =>
                //TO-DO JSON representation of transaction
                ApiResponse("result" -> ("transaction", tx.asJson))
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
                  PublicKey25519PropositionSerializer.getSerializer().parseBytes(element._1.getBytes()),
                  new lang.Long(element._2))
              ).toIndexedSeq

              inputs.foreach(pair => inSum += pair.getKey.value())
              outputs.foreach(pair => outSum += pair.getValue)

              var fee : Long = inSum - outSum

              var regularTransaction = RegularTransaction.create(
                JavaConverters.seqAsJavaList(inputs),
                JavaConverters.seqAsJavaList(outputs),
                fee, System.currentTimeMillis())

              if(req.format)
                {
                  //TO-DO JSON representation of transaction
                  ApiResponse("result" -> ("regularTransaction", regularTransaction.asJson))
                }
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

            try {
              var regularTransaction = createRegularTransactionSimplified_(outputList, fee, wallet, sidechainNodeView)

              if(req.format)
                {
                  //TO-DO JSON representation of transaction
                  ApiResponse("result" -> ("regularTransaction", regularTransaction.asJson))
                }
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

  private def createRegularTransactionSimplified_(
                                                  outputList: List[(String, Long)], fee: Long, wallet : NodeWallet,
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
      new Pair(PublicKey25519PropositionSerializer.getSerializer().parseBytes(element._1.getBytes()), new lang.Long(element._2))).toIndexedSeq

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

            try {
              var regularTransaction = createRegularTransactionSimplified_(outputList, fee, wallet, sidechainNodeView)
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
        val barrier = Await.result(sidechainTransactionActorRef ? BroadcastTransaction(transaction), settings.timeout).asInstanceOf[Future[Unit]]
        onComplete(barrier){
          case Success(result) =>
            ApiResponse("result" -> ("transactionId" -> transaction.id.toString))
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
    case class SendRawTransactionRequest(rawTransaction: String)

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[SendRawTransactionRequest](body)match {
          case Success(req) =>
            var transactionBytes = req.rawTransaction
            companion.parseBytesTry(transactionBytes.getBytes) match {
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
