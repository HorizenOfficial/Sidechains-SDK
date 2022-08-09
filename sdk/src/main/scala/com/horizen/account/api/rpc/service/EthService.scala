package com.horizen.account.api.rpc.service

import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives.onComplete
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.SidechainSettings
import com.horizen.account.api.http.AccountTransactionErrorResponse.GenericTransactionError
import com.horizen.account.api.http.AccountTransactionRestScheme.TransactionIdDTO
import com.horizen.account.api.rpc.handler.RpcException
import com.horizen.account.api.rpc.types._
import com.horizen.account.api.rpc.utils._
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.receipt.EthereumReceipt
import com.horizen.account.state._
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.wallet.AccountWallet
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.api.http.{ApiResponseUtil, SuccessResponse}
import com.horizen.params.NetworkParams
import com.horizen.transaction.Transaction
import com.horizen.utils.ClosableResourceHandler
import org.web3j.crypto.TransactionDecoder
import org.web3j.utils.Numeric
import scorex.core.NodeViewHolder.CurrentView
import scorex.util.ModifierId

import java.math.BigInteger
import java.util.{Optional => JOptional}
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}


class EthService(val stateView: AccountStateView, val nodeView: CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool], val networkParams: NetworkParams, val sidechainSettings: SidechainSettings, val sidechainTransactionActorRef: ActorRef)
  extends RpcService
    with ClosableResourceHandler {

  //function which describes default transaction representation for answer after adding the transaction to a memory pool
  val defaultTransactionResponseRepresentation: Transaction => SuccessResponse = {
    transaction => TransactionIdDTO(transaction.id)
  }

  @RpcMethod("eth_getBlockByNumber") def getBlockByNumber(tag: Quantity, hydratedTx: Boolean): EthereumBlock = {
    constructEthBlockWithTransactions(getBlockIdByTag(tag.getValue), hydratedTx)
  }

  @RpcMethod("eth_getBlockByHash") def getBlockByHash(tag: Quantity, hydratedTx: Boolean): EthereumBlock = {
    constructEthBlockWithTransactions(Numeric.cleanHexPrefix(tag.getValue), hydratedTx)
  }

  private def constructEthBlockWithTransactions(blockId: String, hydratedTx: Boolean): EthereumBlock = {
    if (blockId == null) return null
    nodeView.history.getBlockById(blockId).asScala match {
      case None => null
      case Some(block) =>
        val transactions = block.transactions.filter {
          _.isInstanceOf[EthereumTransaction]
        } map {
          _.asInstanceOf[EthereumTransaction]
        }
        new EthereumBlock(
          Numeric.prependHexPrefix(Integer.toHexString(nodeView.history.getBlockHeight(blockId).get())),
          Numeric.prependHexPrefix(blockId),
          if (!hydratedTx) {
            transactions.map(tx => Numeric.prependHexPrefix(tx.id)).toList.asJava
          } else {
            transactions.flatMap(tx => stateView.getTransactionReceipt(Numeric.hexStringToByteArray(tx.id)) match {
              case Some(receipt) => Some(new EthereumTransactionView(receipt, tx))
              case None => None
            }).toList.asJava
          },
          block,
        )
    }
  }

  private def doCall[A](params: TransactionArgs, tag: Quantity)(fun: (ExecutionSucceeded, AccountStateView) ⇒ A): A = {
    getStateViewAtTag(tag) {
      case None => throw new IllegalArgumentException(s"Unable to get state for given tag: ${tag.getValue}")
      case Some(tagStateView) => tagStateView.applyMessage(params.toMessage(tagStateView.getBaseFee)) match {
          case Some(success: ExecutionSucceeded) => fun(success, tagStateView)

          case Some(failed: ExecutionFailed) =>
            // throw on execution errors, also include evm revert reason if possible
            throw new RpcException(new RpcError(
              RpcCode.ExecutionError.getCode, failed.getReason.getMessage, failed.getReason match {
                case evmRevert: EvmException => Numeric.toHexString(evmRevert.returnData)
                case _ => null
              }))

          case Some(invalid: InvalidMessage) =>
            throw new IllegalArgumentException("Invalid message.", invalid.getReason)

          case _ => throw new IllegalArgumentException("Unable to process call.")
        }
    }
  }

  @RpcMethod("eth_call") def call(params: TransactionArgs, tag: Quantity): String = {
    doCall(params, tag) {
      (result, _) => if (result.hasReturnData) Numeric.toHexString(result.returnData) else null
    }
  }

  @RpcMethod("eth_estimateGas") def estimateGas(params: TransactionArgs /*, tag: Quantity*/): String = {
    doCall(params, new Quantity("latest")) {
      (_, view) => Numeric.toHexStringWithPrefix(view.getGasPool.getUsedGas)
    }
  }

  @RpcMethod("eth_blockNumber") def blockNumber = new Quantity(BigInteger.valueOf(nodeView.history.getCurrentHeight))

  @RpcMethod("eth_chainId") def chainId = new Quantity(BigInteger.valueOf(networkParams.chainId))

  @RpcMethod("eth_getBalance") def GetBalance(address: String, tag: Quantity): Quantity = {
    getStateViewAtTag(tag) {
      case None => new Quantity("null")
      case Some(tagStateView) => new Quantity(tagStateView.getBalance(Numeric.hexStringToByteArray(address)))
    }
  }

  @RpcMethod("eth_getTransactionCount") def getTransactionCount(address: String, tag: Quantity): Quantity = {
    getStateViewAtTag(tag) {
      case None => new Quantity("0x0")
      case Some(tagStateView) => new Quantity(tagStateView.getNonce(Numeric.hexStringToByteArray(address)))
    }
  }

  private def getStateViewAtTag[A](tag: Quantity)(fun: Option[AccountStateView] ⇒ A): A = {
    nodeView.history.getBlockById(getBlockIdByTag(tag.getValue)).asScala match {
      case Some(block) => using(nodeView.state.getStateDbViewFromRoot(block.header.stateRoot)) {
        tagStateView => fun(Some(tagStateView))
      }
      case None => fun(None)
    }
  }

  private def getBlockIdByTag(tag: String): String = {
    val history = nodeView.history
    val blockId = tag match {
      case "earliest" => history.getBlockIdByHeight(1)
      case "finalized" | "safe" => throw new RpcException(RpcError.fromCode(RpcCode.UnknownBlock))
      case "latest" | "pending" => history.getBlockIdByHeight(history.getCurrentHeight)
      case height => history.getBlockIdByHeight(Integer.parseInt(Numeric.cleanHexPrefix(height), 16))
    }
    blockId.orElse(null)
  }

  @RpcMethod("net_version") def version: String = String.valueOf(networkParams.chainId)

  @RpcMethod("eth_gasPrice") def gasPrice: Quantity = {
    getStateViewAtTag(new Quantity("latest")) {
      case None => new Quantity("0x0")
      case Some(tagStateView) => new Quantity(tagStateView.getBaseFee)
    }
  }

  private def getTransactionAndReceipt[A](transactionHash: String)(f: (EthereumTransaction, EthereumReceipt) => A) = {
    stateView.getTransactionReceipt(Numeric.hexStringToByteArray(transactionHash))
      .flatMap(receipt => {
        nodeView.history.blockIdByHeight(receipt.blockNumber)
          .map(ModifierId(_))
          .flatMap(nodeView.history.getStorageBlockById)
          .map(_.transactions(receipt.transactionIndex).asInstanceOf[EthereumTransaction])
          .map(tx => f(tx, receipt))
      })
  }

  @RpcMethod("eth_getTransactionByHash")
  def getTransactionByHash(transactionHash: String): EthereumTransactionView = {
    getTransactionAndReceipt(transactionHash) { (tx, receipt) => new EthereumTransactionView(receipt, tx) }.orNull
  }

  @RpcMethod("eth_getTransactionReceipt")
  def getTransactionReceipt(transactionHash: String): EthereumReceiptView = {
    getTransactionAndReceipt(transactionHash) { (tx, receipt) => new EthereumReceiptView(receipt, tx) }.orNull
  }

  @RpcMethod("eth_sendRawTransaction") def sendRawTransaction(signedTxData: String): Quantity = {
    val tx = new EthereumTransaction(TransactionDecoder.decode(signedTxData))
    validateAndSendTransaction(tx)
    new Quantity(Numeric.prependHexPrefix(tx.id))
  }

  private def validateAndSendTransaction(transaction: Transaction,
                                         transactionResponseRepresentation: Transaction => SuccessResponse = defaultTransactionResponseRepresentation) = {
    implicit val timeout: Timeout = 5 seconds
    val barrier = Await
      .result(sidechainTransactionActorRef ? BroadcastTransaction(transaction), timeout.duration)
      .asInstanceOf[Future[Unit]]
    onComplete(barrier) {
      // TODO: add correct responses
      case Success(_) => ApiResponseUtil.toResponse(transactionResponseRepresentation(transaction))
      case Failure(exp) => ApiResponseUtil.toResponse(GenericTransactionError(
        "GenericTransactionError",
        JOptional.of(exp)))
    }
  }

  @RpcMethod("eth_getCode") def getCode(address: String, tag: Quantity): String = {
    getStateViewAtTag(tag) {
      case None => ""
      case Some(tagStateView) =>
        val code = tagStateView.getCode(Numeric.hexStringToByteArray(address))
        if (code == null)
          "0x"
        else
          Numeric.toHexString(code)
    }
  }
}
