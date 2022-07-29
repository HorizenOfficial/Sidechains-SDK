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
import com.horizen.account.block.AccountBlock
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.state.{AccountState, AccountStateView}
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.wallet.AccountWallet
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.api.http.{ApiResponseUtil, SuccessResponse}
import com.horizen.evm.Evm
import com.horizen.evm.utils.Address
import com.horizen.params.NetworkParams
import com.horizen.transaction.Transaction
import org.web3j.crypto.TransactionDecoder
import org.web3j.utils.Numeric
import scorex.core.NodeViewHolder.CurrentView

import java.math.BigInteger
import java.util
import java.util.{Optional => JOptional}
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}


class EthService(val stateView: AccountStateView, val nodeView: CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool], val networkParams: NetworkParams, val sidechainSettings: SidechainSettings, val sidechainTransactionActorRef: ActorRef) extends RpcService {

  //function which describes default transaction representation for answer after adding the transaction to a memory pool
  val defaultTransactionResponseRepresentation: (Transaction => SuccessResponse) = {
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
    val transactions = getTransactionsFromBlock(block.get())
    val txList: ListBuffer[Any] = ListBuffer()
    transactions.foreach(transaction => {
      if (hydratedTx) {
        val receipt = stateView.getTransactionReceipt(Numeric.hexStringToByteArray(Numeric.cleanHexPrefix(transaction.id)))
        if (!receipt.isEmpty) txList.append(new EthereumTransactionView(receipt.get, transaction))
      } else txList.append(Numeric.prependHexPrefix(transaction.id))
    })
    new EthereumBlock(Numeric.prependHexPrefix(Integer.toHexString(nodeView.history.getBlockHeight(blockId).get())), Numeric.prependHexPrefix(blockId), txList.toList.asJava, block.get())
  }

  private def getTransactionsFromBlock(block: AccountBlock) = {
    val ethTx = new ListBuffer[EthereumTransaction]
    block.transactions.foreach(transaction =>
      if (transaction.isInstanceOf[EthereumTransaction]) ethTx.append(transaction.asInstanceOf[EthereumTransaction]))
    ethTx
  }

  @RpcMethod("eth_call") def call(params: TransactionArgs, tag: Quantity): String = {
    val stateDbRoot = getStateViewFromBlockById(getBlockIdByTag(tag.getValue))
    if (stateDbRoot == null) return null
    val result = Evm.Apply(
      stateDbRoot.getStateDbHandle,
      params.getFrom,
      if (params.to == null) null else params.to.toBytes,
      params.value,
      params.getData,
      params.gas,
      params.gasPrice
    )

    if (result.evmError.nonEmpty) {
      throw new RpcException(
        new RpcError(
          RpcCode.ExecutionError.getCode,
          result.evmError,
          Numeric.toHexString(result.returnData)
        )
      )
    }
    if (result.returnData == null) return null
    Numeric.toHexString(result.returnData)
  }

  @RpcMethod("eth_blockNumber") def blockNumber = new Quantity(Numeric.toHexStringWithPrefix(BigInteger.valueOf(nodeView.history.getCurrentHeight)))

  @RpcMethod("eth_chainId") def chainId = new Quantity(Numeric.toHexStringWithPrefix(BigInteger.valueOf(networkParams.chainId)))

  @RpcMethod("eth_getBalance") def GetBalance(address: String, blockNumberOrTag: Quantity) = new Quantity(getBalance(address, blockNumberOrTag))

  private def getBalance(address: String, tag: Quantity): String = {
    val stateDbRoot = getStateViewFromBlockById(getBlockIdByTag(tag.getValue))
    if (stateDbRoot == null) return "null"
    val balance = stateDbRoot.getBalance(Numeric.hexStringToByteArray(address))
    Numeric.toHexStringWithPrefix(balance)
  }

  @RpcMethod("eth_getTransactionCount") def getTransactionCount(address: String, tag: Quantity): Quantity = {
    val stateDbRoot = getStateViewFromBlockById(getBlockIdByTag(tag.getValue))
    if (stateDbRoot == null) return new Quantity("0x0")
    val nonce = stateDbRoot.getNonce(Numeric.hexStringToByteArray(address))
    new Quantity(Numeric.toHexStringWithPrefix(nonce))
  }

  private def getStateViewFromBlockById(blockId: String): AccountStateView = {
    val block = nodeView.history.getBlockById(blockId)
    if (block.isEmpty) return null
    nodeView.state.getStateDbViewFromRoot(block.get().header.stateRoot)
  }

  private def getBlockIdByTag(tag: String): String = {
    var blockId: java.util.Optional[String] = null
    val history = nodeView.history
    tag match {
      case "earliest" => blockId = nodeView.history.getBlockIdByHeight(1)
      case "finalized" => throw new RpcException(
        new RpcError(
          RpcCode.UnknownBlock.getCode,
          "Unknown Block",
          null
        )
      )
      case "safe" => throw new RpcException(
        new RpcError(
          RpcCode.UnknownBlock.getCode,
          "Unknown Block",
          null
        )
      )
      case "latest" => blockId = history.getBlockIdByHeight(nodeView.history.getCurrentHeight)
      case "pending" => blockId = history.getBlockIdByHeight(nodeView.history.getCurrentHeight)
      case _ => blockId = history.getBlockIdByHeight(Integer.parseInt(Numeric.cleanHexPrefix(tag), 16))
    }
    if (blockId.isEmpty) return null
    blockId.get()
  }

  @RpcMethod("net_version") def version: String = String.valueOf(networkParams.chainId)

  //todo: simplify together with eth_call
  @RpcMethod("eth_estimateGas") def estimateGas(transaction: util.LinkedHashMap[String, String] /*, tag: Quantity*/): String = {
    val parameters: TransactionArgs = new TransactionArgs
    parameters.value = if (transaction.containsKey("value")) Numeric.toBigInt(transaction.get("value")) else null
    parameters.from = if (transaction.containsKey("from")) Address.FromBytes(Numeric.hexStringToByteArray(transaction.get("from"))) else null
    parameters.to = if (transaction.containsKey("to")) Address.FromBytes(Numeric.hexStringToByteArray(transaction.get("to"))) else null
    parameters.data = if (transaction.containsKey("data")) transaction.get("data") else null
    val stateDbRoot = getStateViewFromBlockById(getBlockIdByTag("latest"))
    if (stateDbRoot == null) return null
    val result = Evm.Apply(
      stateDbRoot.getStateDbHandle,
      parameters.getFrom,
      if (parameters.to == null) null else parameters.to.toBytes,
      parameters.value,
      parameters.getData,
      parameters.gas,
      parameters.gasPrice
    )

    if (result.evmError.nonEmpty) {
      throw new RpcException(
        new RpcError(
          RpcCode.ExecutionError.getCode,
          result.evmError,
          Numeric.toHexString(result.returnData)
        )
      )
    }
    Numeric.toHexStringWithPrefix(result.usedGas)
  }

  @RpcMethod("eth_gasPrice") def gasPrice = { // TODO: Get the real gasPrice later
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
                                         transactionResponseRepresentation: (Transaction => SuccessResponse) = defaultTransactionResponseRepresentation) = {
    implicit val timeout: Timeout = 5 seconds
    val barrier = Await.result(sidechainTransactionActorRef ? BroadcastTransaction(transaction), timeout.duration).asInstanceOf[Future[Unit]]
    onComplete(barrier) {
      // TODO: add correct responses
      case Success(_) =>
        ApiResponseUtil.toResponse(transactionResponseRepresentation(transaction))
      case Failure(exp) =>
        ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(exp))
        )
    }
  }

  @RpcMethod("eth_getTransactionReceipt")
  def getTransactionReceipt(transactionHash: String): Any = {
    val receipt = stateView.getTransactionReceipt(Numeric.hexStringToByteArray(Numeric.cleanHexPrefix(transactionHash)))
    if (receipt.isEmpty) return null
    val block = getBlockById(nodeView.history.getBlockIdByHeight(receipt.get.blockNumber).get())
    val tx = block.transactions(receipt.get.transactionIndex)
    new EthereumReceiptView(receipt.get, tx.asInstanceOf[EthereumTransaction])
  }

  private def getBlockById(blockId: String) = nodeView.history.getBlockById(blockId).get()

  @RpcMethod("eth_getCode") def getCode(address: String, tag: Quantity): String = {
    val stateDbRoot = getStateViewFromBlockById(getBlockIdByTag(tag.getValue))
    if (stateDbRoot == null) return ""
    val code = stateDbRoot.stateDb.getCode(Numeric.hexStringToByteArray(address))
    if (code == null)
      return "0x"
    Numeric.toHexString(code)
  }
}
