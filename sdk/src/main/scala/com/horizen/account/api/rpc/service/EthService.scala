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
import com.horizen.account.secret.PrivateKeySecp256k1
import com.horizen.account.state._
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.AccountForwardTransfersHelper.getForwardTransfersForBlock
import com.horizen.account.utils.EthereumTransactionDecoder
import com.horizen.account.wallet.AccountWallet
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.api.http.{ApiResponseUtil, SuccessResponse}
import com.horizen.chain.SidechainBlockInfo
import com.horizen.evm.interop.TraceParams
import com.horizen.evm.utils.Address
import com.horizen.params.NetworkParams
import com.horizen.transaction.Transaction
import com.horizen.transaction.exception.TransactionSemanticValidityException
import com.horizen.utils.{BytesUtils, ClosableResourceHandler, TimeToEpochUtils}
import org.web3j.crypto.Sign.SignatureData
import org.web3j.crypto.{SignedRawTransaction, TransactionEncoder}
import org.web3j.utils.Numeric
import scorex.util.{ModifierId, ScorexLogging}
import sparkz.core.NodeViewHolder
import sparkz.core.NodeViewHolder.CurrentView

import java.math.BigInteger
import java.util
import java.util.{Optional => JOptional}
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class EthService(
    val scNodeViewHolderRef: ActorRef,
    val nvtimeout: FiniteDuration,
    networkParams: NetworkParams,
    val sidechainSettings: SidechainSettings,
    val sidechainTransactionActorRef: ActorRef
) extends RpcService
      with ClosableResourceHandler
      with ScorexLogging {
  type NV = CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool]

  def applyOnAccountView[R](functionToBeApplied: NV => R): R = {
    implicit val timeout: Timeout = new Timeout(nvtimeout)
    val res = scNodeViewHolderRef
      .ask {
        NodeViewHolder.ReceivableMessages.GetDataFromCurrentView { (nodeview: NV) =>
          // wrap any exceptions
          Try(functionToBeApplied(nodeview))
        }
      }
      .asInstanceOf[Future[Try[R]]]
    // return result or rethrow potential exceptions
    Await.result(res, nvtimeout) match {
      case Success(value) => value
      case Failure(exception) =>
        exception match {
          case err: RpcException => throw err
          case reverted: ExecutionRevertedException =>
            throw new RpcException(
              new RpcError(
                RpcCode.ExecutionError.getCode,
                reverted.getMessage,
                Numeric.toHexString(reverted.revertReason)
              )
            )
          case err: ExecutionFailedException =>
            throw new RpcException(new RpcError(RpcCode.ExecutionError.getCode, err.getMessage, null))
          case err: TransactionSemanticValidityException =>
            throw new RpcException(new RpcError(RpcCode.ExecutionError.getCode, err.getMessage, null))
          case _ =>
            log.error("unexpected exception", exception)
            throw exception
        }
    }
  }

  // function which describes default transaction representation for answer after adding the transaction to a memory pool
  val defaultTransactionResponseRepresentation: Transaction => SuccessResponse = { transaction =>
    TransactionIdDTO(transaction.id)
  }

  @RpcMethod("eth_getBlockByNumber")
  def getBlockByNumber(tag: String, hydratedTx: Boolean): EthereumBlock = {
    applyOnAccountView { nodeView =>
      using(nodeView.state.getView) { stateView =>
        constructEthBlockWithTransactions(nodeView, stateView, getBlockIdByTag(nodeView, tag), hydratedTx)
      }
    }
  }

  @RpcMethod("eth_getBlockByHash")
  def getBlockByHash(tag: String, hydratedTx: Boolean): EthereumBlock = {
    applyOnAccountView { nodeView =>
      using(nodeView.state.getView) { stateView =>
        constructEthBlockWithTransactions(nodeView, stateView, ModifierId(Numeric.cleanHexPrefix(tag)), hydratedTx)
      }
    }
  }

  private def constructEthBlockWithTransactions(
      nodeView: NV,
      stateView: AccountStateView,
      blockId: ModifierId,
      hydratedTx: Boolean
  ): EthereumBlock = {
    nodeView.history.getStorageBlockById(blockId) match {
      case None => null
      case Some(block) =>
        val transactions = block.transactions.filter {
          _.isInstanceOf[EthereumTransaction]
        } map {
          _.asInstanceOf[EthereumTransaction]
        }
        new EthereumBlock(
          Numeric.prependHexPrefix(Integer.toHexString(nodeView.history.getBlockHeightById(blockId).get())),
          Numeric.prependHexPrefix(blockId),
          if (!hydratedTx) {
            transactions.map(tx => Numeric.prependHexPrefix(tx.id)).toList.asJava
          } else {
            transactions
              .flatMap(tx =>
                stateView
                  .getTransactionReceipt(Numeric.hexStringToByteArray(tx.id))
                  .map(new EthereumTransactionView(_, tx, block.header.baseFee))
              )
              .toList
              .asJava
          },
          block
        )
    }
  }

  private def doCall[A](nodeView: NV, params: TransactionArgs, tag: String)(
      fun: (Array[Byte], AccountStateView) ⇒ A
  ): A = {
    getStateViewAtTag(nodeView, tag) { (tagStateView, blockContext) =>
      val msg = params.toMessage(blockContext.baseFee)
      fun(tagStateView.applyMessage(msg, new GasPool(msg.getGasLimit), blockContext), tagStateView)
    }
  }

  @RpcMethod("eth_call")
  @RpcOptionalParameters(1)
  def call(params: TransactionArgs, tag: String): String = {
    applyOnAccountView { nodeView =>
      doCall(nodeView, params, tag) { (result, _) =>
        if (result != null) Numeric.toHexString(result) else null
      }
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

  @RpcMethod("eth_signTransaction") def signTransaction(params: TransactionArgs): String = {
    applyOnAccountView { nodeView =>
      var signedTx = params.toTransaction
      getFittingSecret(nodeView.vault, nodeView.state, Option.apply(params.from), params.value) match {
        case Some(secret) =>
          signedTx = signTransactionWithSecret(secret, signedTx)
        case None =>
          return null
      }
      "0x" + BytesUtils.toHexString(
        TransactionEncoder
          .encode(signedTx.getTransaction, signedTx.getTransaction.asInstanceOf[SignedRawTransaction].getSignatureData)
      )
    }
  }

  private def getFittingSecret(
      wallet: AccountWallet,
      state: AccountState,
      fromAddress: Option[Address],
      txValueInWei: BigInteger
  ): Option[PrivateKeySecp256k1] = {
    wallet
      .secretsOfType(classOf[PrivateKeySecp256k1])
      .map(_.asInstanceOf[PrivateKeySecp256k1])
      .find(secret =>
        // if from address is given the secrets public key needs to match, otherwise check all of the secrets
        fromAddress.forall(from => util.Arrays.equals(from.toBytes, secret.publicImage().address())) &&
          // TODO account for gas
          state.getBalance(secret.publicImage.address).compareTo(txValueInWei) >= 0
      )
  }

  private def signTransactionWithSecret(secret: PrivateKeySecp256k1, tx: EthereumTransaction): EthereumTransaction = {
    val messageToSign = tx.messageToSign()
    val msgSignature = secret.sign(messageToSign)
    var signatureData = new SignatureData(msgSignature.getV, msgSignature.getR, msgSignature.getS)
    if (!tx.isEIP1559 && tx.isSigned && tx.getChainId != null) {
      signatureData = TransactionEncoder.createEip155SignatureData(signatureData, tx.getChainId)
    }
    val signedTx = new EthereumTransaction(new SignedRawTransaction(tx.getTransaction.getTransaction, signatureData))
    signedTx
  }

  @RpcMethod("eth_estimateGas")
  @RpcOptionalParameters(1)
  def estimateGas(params: TransactionArgs, tag: String): Quantity = {
    applyOnAccountView { nodeView =>
      // Binary search the gas requirement, as it may be higher than the amount used
      val lowBound = GasUtil.TxGas.subtract(BigInteger.ONE)
      // Determine the highest gas limit can be used during the estimation.
      var highBound = params.gas
      getStateViewAtTag(nodeView, tag) { (tagStateView, blockContext) =>
        if (highBound == null || highBound.compareTo(GasUtil.TxGas) < 0) {
          highBound = BigInteger.valueOf(blockContext.blockGasLimit)
        }
        // Normalize the max fee per gas the call is willing to spend.
        var feeCap = BigInteger.ZERO
        if (params.gasPrice != null && (params.maxFeePerGas != null || params.maxPriorityFeePerGas != null)) {
          throw new RpcException(
            RpcError
              .fromCode(RpcCode.InvalidParams, "both gasPrice and (maxFeePerGas or maxPriorityFeePerGas) specified")
          )
        } else if (params.gasPrice != null) {
          feeCap = params.gasPrice
        } else if (params.maxFeePerGas != null) {
          feeCap = params.maxFeePerGas
        }
        // Recap the highest gas limit with account's available balance.
        if (feeCap.bitLength() > 0) {
          val balance = tagStateView.getBalance(params.getFrom)
          val available = if (params.value == null) { balance }
          else {
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
      val rpcGasCap = GasUtil.RpcGlobalGasCap
      if (highBound.compareTo(rpcGasCap) > 0) {
        highBound = rpcGasCap
      }
      // lambda that tests a given gas limit, returns true on successful execution, false on out-of-gas error
      // other exceptions are not caught as the call would not succeed with any amount of gas
      val check = (gas: BigInteger) =>
        try {
          params.gas = gas
          doCall(nodeView, params, tag) { (_, _) => true }
        } catch {
          case _: ExecutionFailedException => false
          case _: IntrinsicGasException => false
        }
      // Execute the binary search and hone in on an executable gas limit
      // We need to do a search because the gas required during execution is not necessarily equal to the consumed
      // gas after the execution. See https://github.com/ethereum/go-ethereum/commit/682875adff760a29a2bb0024190883e4b4dd5d72
      val requiredGasLimit = binarySearch(lowBound, highBound)(check)
      // Reject the transaction as invalid if it still fails at the highest allowance
      if (requiredGasLimit == highBound && !check(highBound)) {
        throw new RpcException(RpcError.fromCode(RpcCode.InvalidParams, s"gas required exceeds allowance ($highBound)"))
      }
      new Quantity(requiredGasLimit)
    }
  }

  @RpcMethod("eth_blockNumber")
  def blockNumber: Quantity = applyOnAccountView { nodeView =>
    new Quantity(BigInteger.valueOf(nodeView.history.getCurrentHeight))
  }

  @RpcMethod("eth_chainId")
  def chainId: Quantity = new Quantity(BigInteger.valueOf(networkParams.chainId))

  @RpcMethod("eth_getBalance")
  @RpcOptionalParameters(1)
  def getBalance(address: Address, tag: String): Quantity = {
    applyOnAccountView { nodeView =>
      getStateViewAtTag(nodeView, tag) { (tagStateView, _) =>
        new Quantity(tagStateView.getBalance(address.toBytes))
      }
    }
  }

  @RpcMethod("eth_getTransactionCount")
  @RpcOptionalParameters(1)
  def getTransactionCount(address: Address, tag: String): Quantity = {
    applyOnAccountView { nodeView =>
      getStateViewAtTag(nodeView, tag) { (tagStateView, _) =>
        new Quantity(tagStateView.getNonce(address.toBytes))
      }
    }
  }

  private def getBlockByTag(nodeView: NV, tag: String): (AccountBlock, SidechainBlockInfo) = {
    val blockId = getBlockIdByTag(nodeView, tag)
    val block = nodeView.history
      .getStorageBlockById(blockId)
      .getOrElse(throw new RpcException(RpcError.fromCode(RpcCode.UnknownBlock, "Invalid block tag parameter.")))
    val blockInfo = nodeView.history.blockInfoById(blockId)
    (block, blockInfo)
  }

  private def getStateViewAtTag[A](nodeView: NV, tag: String)(fun: (AccountStateView, BlockContext) ⇒ A): A = {
    val (block, blockInfo) = getBlockByTag(nodeView, tag)
    val blockContext = new BlockContext(
      block.header,
      blockInfo.height,
      TimeToEpochUtils.timeStampToEpochNumber(networkParams, blockInfo.timestamp),
      blockInfo.withdrawalEpochInfo.epoch
    )
    using(nodeView.state.getStateDbViewFromRoot(block.header.stateRoot))(fun(_, blockContext))
  }

  private def getBlockIdByTag(nodeView: NV, tag: String): ModifierId = {
    val history = nodeView.history
    val blockId = tag match {
      case "earliest" => history.blockIdByHeight(1)
      case "finalized" | "safe" => throw new RpcException(RpcError.fromCode(RpcCode.UnknownBlock))
      case "latest" | "pending" | null => history.blockIdByHeight(history.getCurrentHeight)
      case height => Try.apply(Numeric.decodeQuantity(height).intValueExact()).toOption.flatMap(history.blockIdByHeight)
    }
    ModifierId(
      blockId
        .getOrElse(throw new RpcException(new RpcError(RpcCode.InvalidParams, "Invalid block tag parameter", null)))
    )
  }

  @RpcMethod("net_version")
  def version: String = String.valueOf(networkParams.chainId)

  @RpcMethod("eth_gasPrice")
  def gasPrice: Quantity = {
    applyOnAccountView { nodeView =>
      getStateViewAtTag(nodeView, "latest") { (_, blockContext) => new Quantity(blockContext.baseFee) }
    }
  }

  private def getTransactionAndReceipt(transactionHash: String) = {
    applyOnAccountView { nodeView =>
      using(nodeView.state.getView) { stateView =>
        stateView
          .getTransactionReceipt(Numeric.hexStringToByteArray(transactionHash))
          .flatMap(receipt => {
            nodeView.history
              .blockIdByHeight(receipt.blockNumber)
              .map(ModifierId(_))
              .flatMap(nodeView.history.getStorageBlockById)
              .map(block => {
                val tx = block.transactions(receipt.transactionIndex).asInstanceOf[EthereumTransaction]
                (block, tx, receipt)
              })
          })
      }
    }
  }

  @RpcMethod("eth_getTransactionByHash")
  def getTransactionByHash(transactionHash: String): EthereumTransactionView = {
    getTransactionAndReceipt(transactionHash).map { case (block, tx, receipt) =>
      new EthereumTransactionView(receipt, tx, block.header.baseFee)
    }.orNull
  }

  @RpcMethod("eth_getTransactionReceipt")
  def getTransactionReceipt(transactionHash: String): EthereumReceiptView = {
    getTransactionAndReceipt(transactionHash).map { case (block, tx, receipt) =>
      new EthereumReceiptView(receipt, tx, block.header.baseFee)
    }.orNull
  }

  @RpcMethod("eth_sendRawTransaction") def sendRawTransaction(signedTxData: String): Quantity = {
    val tx = new EthereumTransaction(EthereumTransactionDecoder.decode(signedTxData))
    validateAndSendTransaction(tx)
    new Quantity(Numeric.prependHexPrefix(tx.id))
  }

  private def validateAndSendTransaction(
      transaction: Transaction,
      transactionResponseRepresentation: Transaction => SuccessResponse = defaultTransactionResponseRepresentation
  ) = {
    implicit val timeout: Timeout = 5 seconds
    val barrier = Await
      .result(sidechainTransactionActorRef ? BroadcastTransaction(transaction), timeout.duration)
      .asInstanceOf[Future[Unit]]
    onComplete(barrier) {
      // TODO: add correct responses
      case Success(_) => ApiResponseUtil.toResponse(transactionResponseRepresentation(transaction))
      case Failure(exp) =>
        ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(exp)))
    }
  }

  @RpcMethod("eth_getCode")
  @RpcOptionalParameters(1)
  def getCode(address: Address, tag: String): String = {
    applyOnAccountView { nodeView =>
      getStateViewAtTag(nodeView, tag) { (tagStateView, _) =>
        val code = tagStateView.getCode(address.toBytes)
        if (code == null)
          "0x"
        else
          Numeric.toHexString(code)
      }
    }
  }

  @RpcMethod("debug_traceBlockByNumber")
  @RpcOptionalParameters(1)
  def traceBlockByNumber(tag: String, traceParams: TraceParams): DebugTraceBlockView = {
    applyOnAccountView { nodeView =>
      // get block to trace
      val (block, blockInfo) = getBlockByTag(nodeView, tag)

      // get state at previous block
      getStateViewAtTag(nodeView, (blockInfo.height - 1).toString) { (tagStateView, blockContext) =>
        // use default trace params if none are given
        blockContext.setTraceParams(if (traceParams == null) new TraceParams() else traceParams)

        // apply mainchain references
        for (mcBlockRefData <- block.mainchainBlockReferencesData) {
          tagStateView.applyMainchainBlockReferenceData(mcBlockRefData).get
        }

        val gasPool = new GasPool(BigInteger.valueOf(block.header.gasLimit))

        // apply all transaction, collecting traces on the way
        val evmResults = block.transactions.zipWithIndex.map({ case (tx, i) =>
          tagStateView.applyTransaction(tx, i, gasPool, blockContext)
          blockContext.getEvmResult
        })

        new DebugTraceBlockView(evmResults.toArray)
      }
    }
  }

  @RpcMethod("debug_traceTransaction")
  @RpcOptionalParameters(1)
  def traceTransaction(transactionHash: String, traceParams: TraceParams): DebugTraceTransactionView = {
    // get block containing the requested transaction
    val (block, blockNumber, requestedTransactionHash) = getTransactionAndReceipt(transactionHash)
      .map { case (block, tx, receipt) =>
        (block, receipt.blockNumber, tx.id)
      }
      .getOrElse(
        throw new RpcException(RpcError.fromCode(RpcCode.InvalidParams, s"transaction not found: $transactionHash"))
      )

    applyOnAccountView { nodeView =>
      getStateViewAtTag(nodeView, (blockNumber - 1).toString) { (tagStateView, blockContext) =>
        // apply mainchain references
        for (mcBlockRefData <- block.mainchainBlockReferencesData) {
          tagStateView.applyMainchainBlockReferenceData(mcBlockRefData).get
        }

        val gasPool = new GasPool(BigInteger.valueOf(block.header.gasLimit))

        // separate transactions within the block to the ones before the requested Tx and the rest
        val (previousTransactions, followingTransactions) = block.transactions.span(_.id != requestedTransactionHash)
        val requestedTx = followingTransactions.head

        // apply previous transactions without tracing
        for ((tx, i) <- previousTransactions.zipWithIndex) {
          tagStateView.applyTransaction(tx, i, gasPool, blockContext)
        }
        // use default trace params if none are given
        blockContext.setTraceParams(if (traceParams == null) new TraceParams() else traceParams)

        // apply requested transaction with tracing enabled
        blockContext.setEvmResult(null)
        tagStateView.applyTransaction(requestedTx, previousTransactions.length, gasPool, blockContext)

        new DebugTraceTransactionView(blockContext.getEvmResult)
      }
    }
  }

  @RpcMethod("eth_getForwardTransfers")
  def getForwardTransfers(blockId: String): ForwardTransfersView = {
    if (blockId == null) return null
    applyOnAccountView { nodeView =>
      nodeView.history.getStorageBlockById(getBlockIdByTag(nodeView, blockId)) match {
        case Some(block) => new ForwardTransfersView(getForwardTransfersForBlock(block).asJava, false)
        case None => null
      }
    }
  }
}
