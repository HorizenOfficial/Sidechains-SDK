package com.horizen.account.api.rpc.service

import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives.onComplete
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.SidechainSettings
import com.horizen.account.api.http.AccountTransactionErrorResponse.GenericTransactionError
import com.horizen.account.api.http.AccountTransactionRestScheme.TransactionIdDTO
import com.horizen.account.api.rpc.handler.RpcException
import com.horizen.account.api.rpc.types.{Data, EthereumBlock, EthereumReceiptView, EthereumTransactionView, Quantity, TransactionArgs}
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
    val blockId = nodeView.history.getBlockIdByHeight(Integer.parseInt(Numeric.cleanHexPrefix(tag.getValue), 16))
    val txHashes: ListBuffer[String] = ListBuffer()
    val txViews: ListBuffer[EthereumTransactionView] = ListBuffer()

    if (blockId.isEmpty) return new EthereumBlock()

    val txs = getTransactionsFromBlock(getBlockById(blockId.get()))
    txs.foreach(transaction => {
      txViews.append(new EthereumTransactionView(transaction))
      txHashes.append(Numeric.prependHexPrefix(transaction.id))
    })
    new EthereumBlock(tag.getValue, Numeric.prependHexPrefix(blockId.get()), txHashes.toList.asJava, txViews.toList.asJava, hydratedTx, nodeView.history.getBlockById(blockId.get()).get())
  }

  def getTransactionsFromBlock(block: AccountBlock) = {
    val ethTx = new ListBuffer[EthereumTransaction]
    block.transactions.foreach(transaction =>
      if (transaction.isInstanceOf[EthereumTransaction]) ethTx.append(transaction.asInstanceOf[EthereumTransaction]))
    ethTx
  }

  def getBlockById(blockId: String) = nodeView.history.getBlockById(blockId).get()

  @RpcMethod("eth_getBlockByHash") def getBlockByHash(tag: Quantity, hydratedTx: Boolean): EthereumBlock = {
    val txHashes: ListBuffer[String] = ListBuffer()
    val txViews: ListBuffer[EthereumTransactionView] = ListBuffer()

    val txs = getTransactionsFromBlock(getBlockById(Numeric.cleanHexPrefix(tag.getValue)))
    txs.foreach(transaction => {
      txViews.append(new EthereumTransactionView(transaction))
      txHashes.append(Numeric.prependHexPrefix(transaction.id))
    })
    new EthereumBlock(Numeric.prependHexPrefix(Integer.toHexString(nodeView.history.getBlockHeight(Numeric.cleanHexPrefix(tag.getValue)).get())), Numeric.prependHexPrefix(tag.getValue), txHashes.toList.asJava, txViews.toList.asJava, hydratedTx, nodeView.history.getBlockById(Numeric.cleanHexPrefix(tag.getValue)).get())
  }

  @RpcMethod("eth_call") def call(params: TransactionArgs, tag: Quantity): Data = {
    val result = Evm.Apply(
      // TODO: get stateView at the given tag (block number, block hash, latest, etc.)
      stateView.getStateDbHandle,
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
    new Data(result.returnData)
  }

  @RpcMethod("eth_blockNumber") def blockNumber = new Quantity(Numeric.toHexStringWithPrefix(BigInteger.valueOf(getBlockHeight)))

  private def getBlockHeight = nodeView.history.getCurrentHeight

  @RpcMethod("eth_chainId") def chainId = new Quantity(Numeric.toHexStringWithPrefix(BigInteger.valueOf(networkParams.chainId)))

  @RpcMethod("eth_getBalance") def GetBalance(address: String, blockNumberOrTag: Quantity) = new Quantity(getBalance(address, blockNumberOrTag))

  private def getBalance(address: String, tag: Quantity) = { // TODO: Add blockNumberOrTag handling
    val balance = nodeView.state.getBalance(Numeric.hexStringToByteArray(address))
    Numeric.toHexStringWithPrefix(balance)
  }

  @RpcMethod("eth_getTransactionCount") def getTransactionCount(address: String, tag: Quantity): Quantity = {
    val nonce = nodeView.state.getNonce(Numeric.hexStringToByteArray(address))
    new Quantity(Numeric.toHexStringWithPrefix(nonce))
  }

  @RpcMethod("net_version") def version: String = String.valueOf(networkParams.chainId)

  @RpcMethod("eth_estimateGas") def estimateGas(transaction: util.LinkedHashMap[String, String] /*, tag: Quantity*/) = { // TODO: We need an estimateGas function to execute EVM and get the gasUsed
    if (transaction.containsKey("data") && transaction.get("data") != null && transaction.get("data").length > 1)
      new Quantity(Numeric.toHexStringWithPrefix(BigInteger.valueOf(3000000))) // particular smart contract creation
    else
      new Quantity(Numeric.toHexStringWithPrefix(BigInteger.valueOf(21000))) // EOA to EOA
  }

  @RpcMethod("eth_gasPrice") def gasPrice = { // TODO: Get the real gasPrice later
    new Quantity("0x1")
  }

  @RpcMethod("eth_getTransactionByHash") def getTransactionByHash(transactionHash: String): EthereumTransactionView = {
    val tx = nodeView.history.searchTransactionInsideBlockchain(Numeric.cleanHexPrefix(transactionHash))
    if (tx.isEmpty) {
      null
    } else {
      new EthereumTransactionView(tx.get().asInstanceOf[EthereumTransaction])
    }
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
  def getTransactionReceipt(transactionHash: String): EthereumReceiptView = {
    val receipt = stateView.getTransactionReceipt(Numeric.hexStringToByteArray(Numeric.cleanHexPrefix(transactionHash)))
    val tx = nodeView.history.searchTransactionInsideBlockchain(Numeric.cleanHexPrefix(transactionHash))
    if (receipt.isEmpty || tx.isEmpty) {
      new EthereumReceiptView()
    } else {
      new EthereumReceiptView(receipt.get, tx.get().asInstanceOf[EthereumTransaction])
    }
  }

  @RpcMethod("eth_getCode") def getCode(address: String, tag: Quantity): String = {
    val code = stateView.stateDb.getCode(Numeric.hexStringToByteArray(address))
    if (code == null)
      return ""
    Numeric.toHexString(code)
  }
}
