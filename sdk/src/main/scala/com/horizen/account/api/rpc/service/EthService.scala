package com.horizen.account.api.rpc.service

import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives.onComplete
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.SidechainSettings
import com.horizen.account.api.http.AccountTransactionErrorResponse.GenericTransactionError
import com.horizen.account.api.http.AccountTransactionRestScheme.TransactionIdDTO
import com.horizen.account.api.rpc.handler.RpcException
import com.horizen.account.api.rpc.types.{EthereumBlock, EthereumReceiptView, EthereumTransactionView}
import com.horizen.account.api.rpc.utils._
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
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils
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

  @RpcMethod("eth_getBlockByNumber") def getBlockByNumber(tag: Quantity, rtnTxObj: Boolean): EthereumBlock = { //TODO: Implement getting block information
    val transactions = nodeView.history.getBlockById(nodeView.history.getBlockIdByHeight(Integer.parseInt(Numeric.cleanHexPrefix(tag.getValue), 16)).get()).get().transactions
    val f: ListBuffer[String] = ListBuffer()
    transactions.foreach(transaction => {
      if (transaction.isInstanceOf[EthereumTransaction]) {
        f.append(Numeric.prependHexPrefix(transaction.asInstanceOf[EthereumTransaction].id))
      }
    })
    val accountBlock = nodeView.history.getBlockById(nodeView.history.getBlockIdByHeight(Integer.parseInt(Numeric.cleanHexPrefix(tag.getValue), 16)).get()).get()
    new EthereumBlock(tag.getValue, Numeric.prependHexPrefix(nodeView.history.getBlockIdByHeight(Integer.parseInt(Numeric.cleanHexPrefix(tag.getValue), 16)).get()), f.toList.asJava, accountBlock)
  }

  @RpcMethod("eth_getBlockByHash") def getBlockByHash(tag: Quantity, rtnTxObj: Boolean): EthereumBlock = { //TODO: Implement getting block information
    val t = nodeView.history.getBlockById(Numeric.cleanHexPrefix(tag.getValue)).get().transactions
    val f: ListBuffer[String] = ListBuffer()
    t.foreach(t => {
      if (t.isInstanceOf[EthereumTransaction]) {
        f.append(Numeric.prependHexPrefix(t.asInstanceOf[EthereumTransaction].id))
      }
    })
    new EthereumBlock(tag.getValue, tag.getValue, f.toList.asJava, nodeView.history.getBlockById(Numeric.cleanHexPrefix(tag.getValue)).get())
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

  @RpcMethod("eth_chainId") def chainId = new Quantity(Numeric.toHexStringWithPrefix(BigInteger.valueOf(networkParams.chainId)))

  @RpcMethod("eth_getBalance") def GetBalance(address: String, blockNumberOrTag: Quantity) = new Quantity(getBalance(address, blockNumberOrTag))

  @RpcMethod("eth_getTransactionCount") def getTransactionCount(address: String, tag: Quantity): Quantity = {
    val nonce = nodeView.state.getNonce(Numeric.hexStringToByteArray(address))
    new Quantity(Numeric.toHexStringWithPrefix(nonce))
  }

  @RpcMethod("net_version") def version: String = Numeric.toHexStringNoPrefix(BigInteger.valueOf(networkParams.chainId))

  @RpcMethod("eth_estimateGas") def estimateGas(transaction: util.LinkedHashMap[String, String]/*, tag: Quantity*/) = { // TODO: We need an estimateGas function to execute EVM and get the gasUsed
    if(transaction.containsKey("data") && transaction.get("data") != null && transaction.get("data").length > 1)
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

  //function which describes default transaction representation for answer after adding the transaction to a memory pool
  val defaultTransactionResponseRepresentation: (Transaction => SuccessResponse) = {
    transaction => TransactionIdDTO(transaction.id)
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
  def getTransactionReceipt(transactionHash: String): EthereumReceiptView = { // TODO: Receipts will be supported later on
    val receipt = stateView.getTransactionReceipt(ByteUtils.fromHexString(Numeric.cleanHexPrefix(transactionHash)))
    val tx = nodeView.history.searchTransactionInsideBlockchain(Numeric.cleanHexPrefix(transactionHash))
    if (receipt.isEmpty || tx.isEmpty) {
      new EthereumReceiptView()
    } else {
      new EthereumReceiptView(receipt.get, tx.get().asInstanceOf[EthereumTransaction])
    }
  }

  @RpcMethod("eth_getCode") def getCode(address: String, tag: Quantity): String = { // TODO: return bytecode of given address or empty
    Numeric.toHexString(stateView.stateDb.getCode(Numeric.hexStringToByteArray(address)))
  }

  private def getBalance(address: String, tag: Quantity) = { // TODO: Add blockNumberOrTag handling
    val balance = nodeView.state.getBalance(Numeric.hexStringToByteArray(address))
    Numeric.toHexStringWithPrefix(balance)
  }

  private def getBlockHeight = nodeView.history.getCurrentHeight
}
