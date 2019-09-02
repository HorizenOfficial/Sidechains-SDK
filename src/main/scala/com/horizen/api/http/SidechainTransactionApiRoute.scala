package com.horizen.api.http

import java.lang.Byte
import java.util.function.Consumer
import java.{lang, util}

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.box.{Box, RegularBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.node.{NodeWallet, SidechainNodeView}
import com.horizen.proposition.{Proposition, PublicKey25519Proposition, PublicKey25519PropositionSerializer}
import com.horizen.secret.PrivateKey25519
import com.horizen.{SidechainTypes, transaction}
import com.horizen.transaction._
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import scorex.core.settings.RESTApiSettings
import io.circe.generic.auto._
import javafx.util.Pair
import io.circe.Json

import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

case class SidechainTransactionApiRoute(override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef,
                                        sidechainTransactionActorRef: ActorRef)(implicit val context: ActorRefFactory, override val ec : ExecutionContext)
      extends SidechainApiRoute {

  override val route : Route = (pathPrefix("transaction"))
            {allTransactions ~ findById ~ decodeTransactionBytes ~ createRegularTransaction ~ createRegularTransactionSimplified ~ sendCoinsToAddress ~ sendTransaction}

  private var companion : SidechainTransactionsCompanion = new SidechainTransactionsCompanion(new util.HashMap[Byte, TransactionSerializer[SidechainTypes#SCBT]]())

  case class TransactionInput(boxId: String)
  case class TransactionOutput(publicKey: String, value: Long)
  case class CreateRegularTransactionRequest(transactionInputs: List[TransactionInput],
                                             transactionOutputs: List[TransactionOutput],
                                             format: Option[Boolean])
  {
    require(transactionInputs.nonEmpty, "Empty inputs list")
    require(transactionOutputs.nonEmpty, "Empty outputs list")
  }

  case class CreateRegularTransactionSimplifiedRequest(transactionOutputs: List[TransactionOutput],
                                                       fee: Long,
                                                       format: Option[Boolean]){
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
  def allTransactions : Route = (post & path("allTransactions"))
  {
    case class AllTransactionsRequest(format: Option[Boolean])

    entity(as[AllTransactionsRequest]) { body =>
      withNodeView{ sidechainNodeView =>
        var unconfirmedTxs = sidechainNodeView.getNodeMemoryPool.allTransactions()
        if(body.format.getOrElse(true)){
          SidechainApiResponse(Json.obj("transactions" -> Json.fromValues(unconfirmedTxs.asScala.map(tx => tx.toJson)))
          )
        } else {
          SidechainApiResponse(Json.obj("transactions" -> Json.fromValues(unconfirmedTxs.asScala.map(tx => Json.fromString(tx.id.toString)))))
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
  def findById : Route = (post & path("findById"))
  {
    case class FindTransactionByidRequest(txId: String, blockHash: Option[String], txIndex: Option[Boolean], format: Option[Boolean])

    entity(as[FindTransactionByidRequest]) { body =>
      withNodeView{ sidechainNodeView =>
        val memoryPool = sidechainNodeView.getNodeMemoryPool
        val history = sidechainNodeView.getNodeHistory

        def searchTransactionInMemoryPool(id : String) : Option[_ <: Transaction] = {
          var opt = memoryPool.getTransactionByid(id)
          if(opt.isPresent)
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

        var txId = body.txId
        var format = body.format.getOrElse(false)
        var blockHash = body.blockHash.getOrElse("")
        var txIndex = body.txIndex.getOrElse(false)
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
              SidechainApiResponse("transaction", t.toJson.asString.get)
            }else{
              SidechainApiResponse("transaction", companion.toBytes(t))
            }
          case None =>
            // TO-DO Change the errorCode
            SidechainApiResponse(
              SidechainApiErrorResponse(
                TransactionApiGroupErrorCodes.NOT_FOUND_ID, error))
        }
      }
    }
  }

  /**
    * Return a JSON representation of a transaction given its byte serialization.
    */
  def decodeTransactionBytes : Route = (post & path("decodeTransactionBytes"))
  {
    case class DecodeTransactionBytesRequest(transactionBytes: String)

    entity(as[DecodeTransactionBytesRequest]) { body =>
      withNodeView{ sidechainNodeView =>
            var bytes = BytesUtils.fromHexString(body.transactionBytes)
            var tryTX = companion.parseBytesTry(BytesUtils.fromHexString(body.transactionBytes))
            tryTX match{
              case Success(tx) =>
                //TO-DO JSON representation of transaction
                SidechainApiResponse(Json.obj(("transaction", tx.toJson)))
              case Failure(exp) =>
                SidechainApiResponse(
                  SidechainApiErrorResponse(
                    TransactionApiGroupErrorCodes.BYTE_PARSING_FAILURE, exp.getMessage))
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

    entity(as[CreateRegularTransactionRequest]) { body =>
      withNodeView{ sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        val inputBoxes = wallet.allBoxes().asScala
          .filter(box => body.transactionInputs.exists(p => p.boxId.contentEquals(BytesUtils.toHexString(box.id()))))

        if(inputBoxes.length < body.transactionInputs.size) {
          SidechainApiResponse(
            SidechainApiErrorResponse(
              TransactionApiGroupErrorCodes.NOT_FOUND_INPUT, s"Unable to find input(s)"))
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

          val outputs : IndexedSeq[Pair[PublicKey25519Proposition, lang.Long]] = body.transactionOutputs.map(element =>
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

            if (body.format.getOrElse(false))
              SidechainApiResponse(Json.obj(("transaction", regularTransaction.toJson)))
            else
              SidechainApiResponse(Json.obj(("transactionHex",
                Json.fromString(BytesUtils.toHexString(companion.toBytes(regularTransaction))))))
          } catch {
            case t : Throwable =>
              SidechainApiResponse(
                SidechainApiErrorResponse(
                  TransactionApiGroupErrorCodes.GENERIC_FAILURE, t.getMessage))
          }
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

    entity(as[CreateRegularTransactionSimplifiedRequest]) { body =>
      withNodeView{ sidechainNodeView =>
        var outputList = body.transactionOutputs
        var fee = body.fee
        val wallet = sidechainNodeView.getNodeWallet

        try {
          var regularTransaction = createRegularTransactionSimplified_(outputList, fee, wallet, sidechainNodeView)

          if(body.format.getOrElse(false))
            SidechainApiResponse(Json.obj(("transaction", regularTransaction.toJson)))
          else
            SidechainApiResponse(Json.obj(("transactionHex",
              Json.fromString(BytesUtils.toHexString(companion.toBytes(regularTransaction))))))
        } catch {
          case t : Throwable =>
            SidechainApiResponse(
              SidechainApiErrorResponse(
                TransactionApiGroupErrorCodes.GENERIC_FAILURE, t.getMessage))
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
    entity(as[SendCoinsToAddressesRequest]) { body =>
      withNodeView{ sidechainNodeView =>
        var outputList = body.outputs
        var fee = body.fee
        val wallet = sidechainNodeView.getNodeWallet

        try {
          var regularTransaction = createRegularTransactionSimplified_(outputList, fee.getOrElse(0L), wallet, sidechainNodeView)
          validateAndSendTransaction(regularTransaction)
        } catch {
          case t : Throwable =>
            SidechainApiResponse(
              SidechainApiErrorResponse(
                TransactionApiGroupErrorCodes.GENERIC_FAILURE, t.getMessage))
        }
      }
    }
  }

  private def validateAndSendTransaction(transaction : Transaction) = {
    withNodeView{
      sidechainNodeView =>
        val barrier = Await.result(
          sidechainTransactionActorRef ? BroadcastTransaction(transaction),
          settings.timeout).asInstanceOf[Future[Unit]]
        onComplete(barrier){
          case Success(result) =>
            SidechainApiResponse(Json.obj("transactionId" -> Json.fromString(transaction.id)))
          case Failure(exp) =>
            SidechainApiResponse(
              SidechainApiErrorResponse(
                TransactionApiGroupErrorCodes.GENERIC_FAILURE, exp.getMessage))
        }
    }
  }

  /**
    * Validate and send a transaction, given its serialization as input.
    * Return error in case of invalid transaction or parsing error, otherwise return the id of the transaction.
    */
  def sendTransaction : Route = (post & path("sendTransaction"))
  {
    case class SendTransactionRequest(transactionBytes: String)

    entity(as[SendTransactionRequest]) { body =>
      withNodeView { sidechainNodeView =>
        var transactionBytes = BytesUtils.fromHexString(body.transactionBytes)
        companion.parseBytesTry(transactionBytes) match {
          case Success(transaction) =>
            validateAndSendTransaction(transaction)
          case Failure(exception) =>
            SidechainApiResponse(
              SidechainApiErrorResponse(
                TransactionApiGroupErrorCodes.GENERIC_FAILURE, exception.getMessage))
        }
      }
    }
  }

}
