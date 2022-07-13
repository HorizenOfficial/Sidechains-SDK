package com.horizen.account.api.rpc.service

import akka.actor.ActorRef
import akka.pattern.ask
import akka.http.scaladsl.server.Directives.onComplete
import akka.util.Timeout
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.{SidechainSettings, SidechainTypes}
import com.horizen.account.api.http.AccountTransactionErrorResponse.{ErrorInsufficientBalance, GenericTransactionError}
import com.horizen.account.api.http.AccountTransactionRestScheme.TransactionIdDTO
import com.horizen.account.api.rpc.utils.{Data, Quantity, ResponseObject}
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.state.{AccountState, AccountStateView, EvmMessageProcessor, ExecutionSucceeded, Message}
import com.horizen.account.transaction.AccountTransaction
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.wallet.AccountWallet
import com.horizen.api.http.{ApiResponseUtil, SuccessResponse}
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.params.NetworkParams
import com.horizen.proof.Proof
import com.horizen.proposition.Proposition
import com.horizen.serialization.Views
import com.horizen.transaction.Transaction
import org.web3j.crypto.TransactionDecoder
import org.web3j.crypto.transaction.`type`.TransactionType
import org.web3j.protocol.core.methods.response.{EthBlock, Log, TransactionReceipt, Transaction => Web3jTransaction}
import org.web3j.utils.Numeric
import scorex.core.NodeViewHolder.CurrentView
import scorex.crypto.hash.Keccak256

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}
import scala.language.postfixOps
import java.util.{Optional => JOptional}
import java.math.BigInteger
import java.util


class EthService(val stateView: AccountStateView, val nodeView: CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool], val networkParams: NetworkParams, val sidechainSettings: SidechainSettings, val sidechainTransactionActorRef: ActorRef) extends RpcService {

  @RpcMethod("eth_getBlockByNumber") def getBlockByNumber(tag: Quantity, rtnTxObj: Boolean): EthBlock.Block = { //TODO: Implement getting block information
    new EthBlock.Block("0x1", "0x56", "0x57", "0x58", "0x59", "0x0", "0x0", "0x0", "0x0", "0", "0", "0", "0", "0", "0", "1", "3000", "2000", "22", new util.ArrayList[EthBlock.TransactionResult[_]], new util.ArrayList[String], new util.ArrayList[String], "")
  }


  @RpcMethod("eth_call") def call(ethCall: util.LinkedHashMap[String, String], tag: Quantity): Data = { // Executes a new message call immediately without creating a transaction on the block chain
    // TODO: add read-only evm execution and cleanup here
    var processor = new EvmMessageProcessor();
    val msg = new Message(
      new AddressProposition(Numeric.hexStringToByteArray("0xA83035b4E719827f146C91b119508ea452c77F35")),
      new AddressProposition(Numeric.hexStringToByteArray(ethCall.get("to"))),
      BigInteger.ZERO,
      BigInteger.ZERO,
      BigInteger.ZERO,
      BigInteger.ZERO,
      BigInteger.ZERO,
      BigInteger.ZERO,
      Numeric.hexStringToByteArray(ethCall.get("data")))
    if (processor.canProcess(msg, stateView)) {
      var result = processor.process(msg, stateView)
      if (!result.isFailed)
        return new Data(result.asInstanceOf[ExecutionSucceeded].returnData())
    }
    return new Data(null)
  }

  @RpcMethod("eth_blockNumber") def blockNumber = new Quantity(Numeric.toHexStringWithPrefix(BigInteger.valueOf(getBlockHeight)))

  @RpcMethod("eth_chainId") def chainId = new Quantity(Numeric.toHexStringWithPrefix(BigInteger.valueOf(networkParams.chainId)))

  @RpcMethod("eth_getBalance") def GetBalance(address: String, blockNumberOrTag: Quantity) = new Quantity(getBalance(address, blockNumberOrTag))

  @RpcMethod("eth_getTransactionCount") def getTransactionCount(address: String, tag: Quantity): Quantity = {
    val nonce = nodeView.state.getNonce(Numeric.hexStringToByteArray(address))
    new Quantity(Numeric.toHexStringWithPrefix(nonce))
  }

  @RpcMethod("net_version") def version: String = Numeric.toHexStringWithPrefix(BigInteger.TEN) // TODO: sidechainSettings.genesisData.mcNetwork or just a number????

  @RpcMethod("eth_estimateGas") def estimateGas(transaction: util.LinkedHashMap[String, String]/*, tag: Quantity*/) = { // TODO: We need an estimateGas function to execute EVM and get the gasUsed
    if(transaction.containsKey("data") && transaction.get("data") != null && transaction.get("data").length > 1)
      new Quantity(Numeric.toHexStringWithPrefix(BigInteger.valueOf(3000000))) // particular smart contract creation
    else
      new Quantity(Numeric.toHexStringWithPrefix(BigInteger.valueOf(21000))) // EOA to EOA
  }

  @RpcMethod("eth_gasPrice") def gasPrice = { // TODO: Get the real gasPrice later
    new Quantity("0x20")
  }

  @RpcMethod("eth_getTransactionByHash") def getTransactionByHash(transactionHash: Quantity): ResponseObject = {
    val tx = getTransaction(transactionHash)
    if (tx.isEmpty) return null
    val ethTx = getEthereumTransaction(tx)
    new ResponseObject(getTxObject(transactionHash, ethTx, tx))
  }

  @RpcMethod("eth_sendRawTransaction") def sendRawTransaction(signedTxData: String): Data = {
    val tx = new EthereumTransaction(TransactionDecoder.decode(signedTxData))
    validateAndSendTransaction(tx)
    if (tx.getTransaction.getType() == TransactionType.LEGACY) return new Data(Keccak256.hash(signedTxData));
    else return new Data(Keccak256.prefixedHash(tx.version(), Numeric.hexStringToByteArray(signedTxData)));
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

  private def getTxObject(transactionHash: Quantity, ethTx: EthereumTransaction, tx: JOptional[AccountTransaction[Proposition, Proof[Proposition]]]) = new Web3jTransaction(transactionHash.getValue, Numeric.toHexStringWithPrefix(tx.get.getNonce), String.valueOf(getBestBlock.hashCode), blockNumber.getValue, chainId.getValue, String.valueOf(tx.get.id), Numeric.toHexString(getFrom(tx).address), Numeric.toHexString(getTo(tx).address), Numeric.toHexStringWithPrefix(tx.get.getValue), Numeric.toHexStringWithPrefix(tx.get.getGasLimit), Numeric.toHexStringWithPrefix(tx.get.getGasPrice), Numeric.toHexString(ethTx.getData), null, // TODO: creates contract hash
    Numeric.toHexString(getFrom(tx).pubKeyBytes), Numeric.toHexString(ethTx.getData), Numeric.toHexString(ethTx.getSignature.getR), Numeric.toHexString(ethTx.getSignature.getS), Numeric.toBigInt(getEthereumTransaction(tx).getSignature.getV).longValue(), String.valueOf(getEthereumTransaction(tx).version), Numeric.toHexStringWithPrefix(ethTx.getMaxFeePerGas), Numeric.toHexStringWithPrefix(ethTx.getMaxPriorityFeePerGas), new util.ArrayList[Any]()) // TODO: access list)

  @RpcMethod("eth_getTransactionReceipt")
  def getTransactionReceipt(transactionHash: Data): TransactionReceipt = { // TODO: Receipts will be supported later on
    new TransactionReceipt(Numeric.toHexString(transactionHash.getValue), "0x0", // TODO: get transaction index
      "0x0", // get block hash
      "0x0", // get block number
      "0x0", // get cumulative gas used
      "0x0", // get gas used
      "null", // return contract address if one is created
      "null", // return root
      "0x1", // return status
      "0x0", // add from
      "0x0", // add to
      new util.ArrayList[Log](),// return logs
      "0x0", // return logsBloom
      "0x0", // insert revert reason
      "0x0", // insert tx type
      "0x0" // insert effective gas price
    )
  }

  @RpcMethod("eth_getCode") def getCode(address: String, tag: Quantity): String = { // TODO: return bytecode of given address or empty
    ""
  }

  private def getBalance(address: String, tag: Quantity) = { // TODO: Add blockNumberOrTag handling
    val balance = nodeView.state.getBalance(Numeric.hexStringToByteArray(address))
    Numeric.toHexStringWithPrefix(balance)
  }

  private def getTransaction(transactionHash: Quantity) = {
    val transactionId = Numeric.cleanHexPrefix(transactionHash.getValue)
    nodeView.history.searchTransactionInsideBlockchain(transactionId)
  }

  private def getEthereumTransaction(tx: JOptional[AccountTransaction[Proposition, Proof[Proposition]]]) = tx.get.getSignature.asInstanceOf[EthereumTransaction]

  private def getFrom(tx: JOptional[AccountTransaction[Proposition, Proof[Proposition]]]) = tx.get.getFrom.asInstanceOf[AddressProposition]

  private def getTo(tx: JOptional[AccountTransaction[Proposition, Proof[Proposition]]]) = tx.get.getTo.asInstanceOf[AddressProposition]

  private def getBlockHeight = nodeView.history.getCurrentHeight

  private def getBestBlock = nodeView.history.getBestBlock
}
