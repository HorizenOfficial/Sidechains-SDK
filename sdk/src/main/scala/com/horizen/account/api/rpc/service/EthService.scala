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
import com.horizen.evm.utils.Address
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

  @RpcMethod("eth_getBlockByNumber")
  def getBlockByNumber(tag: String, hydratedTx: Boolean): EthereumBlock = {
    constructEthBlockWithTransactions(getBlockIdByTag(tag), hydratedTx)
  }

  @RpcMethod("eth_getBlockByHash")
  def getBlockByHash(tag: String, hydratedTx: Boolean): EthereumBlock = {
    constructEthBlockWithTransactions(Numeric.cleanHexPrefix(tag), hydratedTx)
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

  private def doCall[A](params: TransactionArgs, tag: String)(fun: (ExecutionSucceeded, AccountStateView) ⇒ A): A = {
    getStateViewAtTag(tag) { tagStateView =>
      tagStateView.applyMessage(params.toMessage(tagStateView.getBaseFee)) match {
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

  @RpcMethod("eth_call")
  def call(params: TransactionArgs, tag: String): String = {
    doCall(params, tag) {
      (result, _) => if (result.hasReturnData) Numeric.toHexString(result.returnData) else null
    }
  }

  def binarySearch(lowBound: BigInteger, highBound: BigInteger)(fun: BigInteger => Boolean): BigInteger = {
    var low = lowBound
    var high = highBound
    while (low.add(BigInteger.ONE).compareTo(high) < 0) {
      val mid = high.add(low).divide(BigInteger.TWO)
      if (fun(mid)) {
        // on success lower the upper bound
        high = mid
      } else {
        // on failure raise the lower bound
        low = mid
      }
    }
    high
  }

  @RpcMethod("eth_estimateGas")
  @RpcOptionalParameters(1)
  def estimateGas(params: TransactionArgs, tag: String): Quantity = {
    // Binary search the gas requirement, as it may be higher than the amount used
    val lowBound = GasCalculator.TxGas.subtract(BigInteger.ONE)
    // Determine the highest gas limit can be used during the estimation.
    var highBound = params.gas
    getStateViewAtTag(tag) { tagStateView =>
      if (highBound == null || highBound.compareTo(GasCalculator.TxGas) < 0) {
        // TODO: get block gas limit
        highBound = BigInteger.valueOf(30000000)
      }
      // Normalize the max fee per gas the call is willing to spend.
      var feeCap = BigInteger.ZERO
      if (params.gasPrice != null && (params.maxFeePerGas != null || params.maxPriorityFeePerGas != null)) {
        throw new RpcException(RpcError.fromCode(RpcCode.InvalidParams, "both gasPrice and (maxFeePerGas or maxPriorityFeePerGas) specified"))
      } else if (params.gasPrice != null) {
        feeCap = params.gasPrice
      } else if (params.maxFeePerGas != null) {
        feeCap = params.maxFeePerGas
      }
      // Recap the highest gas limit with account's available balance.
      if (feeCap.bitLength() > 0) {
        val balance = tagStateView.getBalance(params.getFrom)
        val available = if (params.value == null) { balance } else {
          if (params.value.compareTo(balance) >= 0)
            throw new RpcException(RpcError.fromCode(RpcCode.InvalidParams, "insufficient funds for transfer"))
          balance.subtract(params.value)
        }
        val allowance = available.divide(feeCap)
        if (highBound.compareTo(allowance) > 0) {
          highBound = allowance
        }
      }
    }
    // Recap the highest gas allowance with specified gascap.
    // global RPC gas cap (in geth this is a config variable)
    val rpcGasCap = BigInteger.valueOf(50000000)
    if (highBound.compareTo(rpcGasCap) > 0) {
      highBound = rpcGasCap
    }
    // Execute the binary search and hone in on an executable gas limit
    val requiredGasLimit = binarySearch(lowBound, highBound) { testGasLimit =>
      params.gas = testGasLimit
      doCall(params, tag) { (result, _) => !result.isFailed }
    }
    // Reject the transaction as invalid if it still fails at the highest allowance
    if (requiredGasLimit == highBound) {
      params.gas = highBound
      if (doCall(params, tag) { (result, _) => result.isFailed }) {
        throw new RpcException(RpcError.fromCode(RpcCode.InvalidParams, s"gas required exceeds allowance ($highBound)"))
      }
    }
    new Quantity(requiredGasLimit)
  }

  @RpcMethod("eth_blockNumber")
  def blockNumber = new Quantity(BigInteger.valueOf(nodeView.history.getCurrentHeight))

  @RpcMethod("eth_chainId")
  def chainId = new Quantity(BigInteger.valueOf(networkParams.chainId))

  @RpcMethod("eth_getBalance")
  def GetBalance(address: Address, tag: String): Quantity = {
    getStateViewAtTag(tag) { tagStateView =>
      new Quantity(tagStateView.getBalance(address.toBytes))
    }
  }

  @RpcMethod("eth_getTransactionCount")
  def getTransactionCount(address: Address, tag: String): Quantity = {
    getStateViewAtTag(tag) { tagStateView =>
      new Quantity(tagStateView.getNonce(address.toBytes))
    }
  }

  private def getStateViewAtTag[A](tag: String)(fun: AccountStateView ⇒ A): A = {
    nodeView.history.getBlockById(getBlockIdByTag(tag)).asScala match {
      case Some(block) => using(nodeView.state.getStateDbViewFromRoot(block.header.stateRoot)) {
        tagStateView => fun(tagStateView)
      }
      case None => throw new RpcException(RpcError.fromCode(RpcCode.InvalidParams, "Invalid block tag parameter."))
    }
  }

  private def getBlockIdByTag(tag: String): String = {
    val history = nodeView.history
    val blockId = tag match {
      case "earliest" => history.getBlockIdByHeight(1)
      case "finalized" | "safe" => throw new RpcException(RpcError.fromCode(RpcCode.UnknownBlock))
      case "latest" | "pending" => history.getBlockIdByHeight(history.getCurrentHeight)
      case height => history.getBlockIdByHeight(Numeric.decodeQuantity(height).intValueExact())
    }
    blockId.orElse(null)
  }

  @RpcMethod("net_version")
  def version: String = String.valueOf(networkParams.chainId)

  @RpcMethod("eth_gasPrice")
  def gasPrice: Quantity = {
    getStateViewAtTag("latest") { tagStateView => new Quantity(tagStateView.getBaseFee) }
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

  private def validateAndSendTransaction(
        transaction: Transaction,
        transactionResponseRepresentation: Transaction => SuccessResponse =
        defaultTransactionResponseRepresentation) = {
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

  @RpcMethod("eth_getCode")
  def getCode(address: Address, tag: String): String = {
    getStateViewAtTag(tag) { tagStateView =>
        val code = tagStateView.getCode(address.toBytes)
        if (code == null)
          "0x"
        else
          Numeric.toHexString(code)
    }
  }
}
