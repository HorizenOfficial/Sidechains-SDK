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

import java.math.BigInteger
import java.util.{Optional => JOptional}
import scala.collection.JavaConverters.seqAsJavaListConverter
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
    val block = nodeView.history.getBlockById(blockId)
    if (block.isEmpty) return null
    // TODO: why does this not compile?
    //val transactions = block.get().transactions.collect { case tx: EthereumTransaction => tx }
    val transactions = block.get().transactions.filter {
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
      block.get())
  }

  private def doCall(params: TransactionArgs, tag: Quantity) = {
    using(getStateViewFromBlockById(getBlockIdByTag(tag.getValue))) { stateDbRoot =>
      if (stateDbRoot == null) {
        throw new IllegalArgumentException(s"Unable to get state for given tag: ${tag.getValue}")
      }
      val message = params.toMessage

      // TODO: refactor to reuse parts of AccountStateView.applyTransaction?
      val messageProcessors = Seq(
        EoaMessageProcessor,
        WithdrawalMsgProcessor,
        ForgerStakeMsgProcessor(networkParams),
        new EvmMessageProcessor()
      )

      val processor = messageProcessors.find(_.canProcess(message, stateView)).getOrElse(
        throw new IllegalArgumentException("Unable to process call.")
      )

      processor.process(message, stateView) match {
        case failed: ExecutionFailed =>
          // throw on execution errors, also include evm revert reason if possible
          throw new RpcException(new RpcError(
            RpcCode.ExecutionError.getCode, failed.getReason.getMessage, failed.getReason match {
              case evmRevert: EvmException => Numeric.toHexString(evmRevert.returnData)
              case _ => null
            }))

        case invalid: InvalidMessage =>
          throw new IllegalArgumentException("Invalid message.", invalid.getReason)

        case success: ExecutionSucceeded => success
      }
    }
  }

  @RpcMethod("eth_call") def call(params: TransactionArgs, tag: Quantity): String = {
    val result = doCall(params, tag)
    if (result.hasReturnData)
      Numeric.toHexString(result.returnData)
    else
      null
  }

  @RpcMethod("eth_estimateGas") def estimateGas(params: TransactionArgs /*, tag: Quantity*/): String = {
    Numeric.toHexStringWithPrefix(doCall(params, new Quantity("latest")).gasUsed())
  }

  @RpcMethod("eth_blockNumber") def blockNumber = new Quantity(Numeric.toHexStringWithPrefix(BigInteger.valueOf(nodeView.history.getCurrentHeight)))

  @RpcMethod("eth_chainId") def chainId = new Quantity(Numeric.toHexStringWithPrefix(BigInteger.valueOf(networkParams.chainId)))

  @RpcMethod("eth_getBalance") def GetBalance(address: String, blockNumberOrTag: Quantity) = new Quantity(getBalance(address, blockNumberOrTag))

  private def getBalance(address: String, tag: Quantity): String = {
    using(getStateViewFromBlockById(getBlockIdByTag(tag.getValue))) { stateDbRoot =>
      if (stateDbRoot == null)
        return "null"
      else {
        val balance = stateDbRoot.getBalance(Numeric.hexStringToByteArray(address))
        Numeric.toHexStringWithPrefix(balance)
      }
    }
  }

  @RpcMethod("eth_getTransactionCount") def getTransactionCount(address: String, tag: Quantity): Quantity = {
    using(getStateViewFromBlockById(getBlockIdByTag(tag.getValue))) { stateDbRoot =>
      if (stateDbRoot == null)
       return new Quantity("0x0")
      else {
        val nonce = stateDbRoot.getNonce(Numeric.hexStringToByteArray(address))
        new Quantity(Numeric.toHexStringWithPrefix(nonce))
      }
    }
  }

  private def getStateViewFromBlockById(blockId: String): AccountStateView = {
    val block = nodeView.history.getBlockById(blockId)
    if (block.isEmpty)
      null
    else
      nodeView.state.getStateDbViewFromRoot(block.get().header.stateRoot)
  }

  private def getBlockIdByTag(tag: String): String = {
    var blockId: java.util.Optional[String] = null
    val history = nodeView.history
    tag match {
      case "earliest" => blockId = history.getBlockIdByHeight(1)
      case "finalized" | "safe" => throw new RpcException(RpcError.fromCode(RpcCode.UnknownBlock))
      case "latest" | "pending" => blockId = history.getBlockIdByHeight(history.getCurrentHeight)
      case height => blockId = history.getBlockIdByHeight(Integer.parseInt(Numeric.cleanHexPrefix(height), 16))
    }
    if (blockId.isEmpty) return null
    blockId.get()
  }

  @RpcMethod("net_version") def version: String = String.valueOf(networkParams.chainId)

  @RpcMethod("eth_gasPrice") def gasPrice: Quantity = {
    // TODO: Get the real gasPrice later
    new Quantity("0x3B9ACA00")
  }

  @RpcMethod("eth_getTransactionByHash") def getTransactionByHash(transactionHash: String): EthereumTransactionView = {
    val receipt = stateView.getTransactionReceipt(Numeric.hexStringToByteArray(Numeric.cleanHexPrefix(transactionHash)))
    if (receipt.isEmpty) return null
    val block = getBlockById(nodeView.history.getBlockIdByHeight(receipt.get.blockNumber).get())
    val tx = block.transactions(receipt.get.transactionIndex)
    new EthereumTransactionView(receipt.get, tx.asInstanceOf[EthereumTransaction])
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
      case Success(_) => ApiResponseUtil.toResponse(transactionResponseRepresentation(transaction))
      case Failure(exp) => ApiResponseUtil.toResponse(GenericTransactionError(
        "GenericTransactionError",
        JOptional.of(exp)))
    }
  }

  @RpcMethod("eth_getTransactionReceipt")
  def getTransactionReceipt(transactionHash: String): Any = {
    stateView.getTransactionReceipt(Numeric.hexStringToByteArray(transactionHash)) match {
      case Some(receipt) => {
        val block = getBlockById(nodeView.history.getBlockIdByHeight(receipt.blockNumber).get())
        val tx = block.transactions(receipt.transactionIndex)
        new EthereumReceiptView(receipt, tx.asInstanceOf[EthereumTransaction])
      }
      case None => null
    }
  }

  private def getBlockById(blockId: String) = nodeView.history.getBlockById(blockId).get()

  @RpcMethod("eth_getCode") def getCode(address: String, tag: Quantity): String = {
    using(getStateViewFromBlockById(getBlockIdByTag(tag.getValue))) { stateDbRoot =>
      if (stateDbRoot == null)
        return ""
      else {
        val code = stateDbRoot.getCode(Numeric.hexStringToByteArray(address))
        if (code == null)
          "0x"
        else
          Numeric.toHexString(code)
      }
    }
  }
}
