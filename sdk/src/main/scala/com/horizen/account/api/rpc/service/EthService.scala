package com.horizen.account.api.rpc.service

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.SidechainSettings
import com.horizen.account.api.rpc.handler.RpcException
import com.horizen.account.api.rpc.types._
import com.horizen.account.api.rpc.utils._
import com.horizen.account.block.AccountBlock
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.proof.SignatureSecp256k1
import com.horizen.account.receipt.Bloom
import com.horizen.account.secret.PrivateKeySecp256k1
import com.horizen.account.state._
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.AccountForwardTransfersHelper.getForwardTransfersForBlock
import com.horizen.account.utils.EthereumTransactionDecoder
import com.horizen.account.utils.FeeUtils.calculateNextBaseFee
import com.horizen.account.wallet.AccountWallet
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.chain.SidechainBlockInfo
import com.horizen.evm.interop.TraceParams
import com.horizen.evm.utils.{Address, Hash}
import com.horizen.params.NetworkParams
import com.horizen.transaction.exception.TransactionSemanticValidityException
import com.horizen.utils.{ClosableResourceHandler, TimeToEpochUtils}
import org.web3j.utils.Numeric
import scorex.util.{ModifierId, ScorexLogging}
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.{NodeViewHolder, bytesToId}

import java.math.BigInteger
import java.util
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.collection.mutable
import scala.concurrent.duration.{FiniteDuration, SECONDS}
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

  private def applyOnAccountView[R](functionToBeApplied: NV => R): R = {
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
              new RpcError(RpcCode.ExecutionError.code, reverted.getMessage, Numeric.toHexString(reverted.revertReason))
            )
          case err: ExecutionFailedException =>
            throw new RpcException(new RpcError(RpcCode.ExecutionError.code, err.getMessage, null))
          case err: TransactionSemanticValidityException =>
            throw new RpcException(new RpcError(RpcCode.ExecutionError.code, err.getMessage, null))
          case _ =>
            log.error("unexpected exception", exception)
            throw exception
        }
    }
  }

  @RpcMethod("eth_getBlockByNumber")
  def getBlockByNumber(tag: String, hydratedTx: Boolean): EthereumBlockView = {
    applyOnAccountView { nodeView =>
      constructEthBlockWithTransactions(nodeView, getBlockIdByTag(nodeView, tag), hydratedTx)
    }
  }

  @RpcMethod("eth_getBlockByHash")
  def getBlockByHash(hash: Hash, hydratedTx: Boolean): EthereumBlockView = {
    applyOnAccountView { nodeView =>
      constructEthBlockWithTransactions(nodeView, bytesToId(hash.toBytes), hydratedTx)
    }
  }

  private def constructEthBlockWithTransactions(
      nodeView: NV,
      blockId: ModifierId,
      hydratedTx: Boolean
  ): EthereumBlockView = {
    nodeView.history
      .getStorageBlockById(blockId)
      .map(block => {
        // retrieve ethereum transactions details and receipt only if the hydratedTx flag is true
        val txViewList = mutable.MutableList[EthereumTransactionView]()
        if(hydratedTx) {
          using(nodeView.state.getView) { stateView =>
            for (blockTx <- block.transactions) {
              val txReceipt = stateView.getTransactionReceipt(Numeric.hexStringToByteArray(blockTx.id)).get
              val ethTx = blockTx.asInstanceOf[EthereumTransaction]
              val txView = new EthereumTransactionView(txReceipt, ethTx, block.header.baseFee)
              txViewList += txView
            }
          }
        }
        // create the return EthereumBlockView
        new EthereumBlockView(
          nodeView.history.getBlockHeightById(blockId).get().toLong,
          Numeric.prependHexPrefix(blockId),
          hydratedTx,
          block,
          txViewList.asJava
        )
      })
      .orNull
  }

  @RpcMethod("eth_getBlockTransactionCountByHash")
  def getBlockTransactionCountByHash(hash: Hash): Quantity = {
    blockTransactionCount(_ => bytesToId(hash.toBytes))
  }

  @RpcMethod("eth_getBlockTransactionCountByNumber")
  def getBlockTransactionCountByNumber(tag: String): Quantity = {
    blockTransactionCount(nodeView => getBlockIdByTag(nodeView, tag))
  }

  private def blockTransactionCount(getBlockId: NV => ModifierId): Quantity = {
    applyOnAccountView { nodeView =>
      nodeView.history
        .getStorageBlockById(getBlockId(nodeView))
        .map(_.transactions.size)
        .map(new Quantity(_))
        .orNull
    }
  }

  private def doCall(nodeView: NV, params: TransactionArgs, tag: String): Array[Byte] = {
    getStateViewAtTag(nodeView, tag) { (tagStateView, blockContext) =>
      val msg = params.toMessage(blockContext.baseFee)
      tagStateView.applyMessage(msg, new GasPool(msg.getGasLimit), blockContext)
    }
  }

  @RpcMethod("eth_call")
  @RpcOptionalParameters(1)
  def call(params: TransactionArgs, tag: String): String = {
    applyOnAccountView { nodeView =>
      Option.apply(doCall(nodeView, params, tag)).map(Numeric.toHexString).orNull
    }
  }

  @RpcMethod("eth_sendTransaction")
  def sendTransaction(params: TransactionArgs): String = {
    val tx = signTransaction(params)
    sendRawTransaction(tx)
  }

  @RpcMethod("eth_signTransaction")
  def signTransaction(params: TransactionArgs): String = {
    applyOnAccountView { nodeView =>
      getFittingSecret(nodeView.vault, nodeView.state, Option.apply(params.from), params.value)
        .map(secret => signTransactionWithSecret(secret, params.toTransaction(networkParams)))
        .map(tx => Numeric.toHexString(tx.encode(tx.isSigned)))
        .orNull
    }
  }

  /**
   * Sign calculates an ECDSA signature for: keccack256("\x19Ethereum Signed Message:\n" + len(message) + message). This
   * gives context to the signed message and prevents signing of transactions.
   */
  @RpcMethod("eth_sign")
  def sign(sender: Address, message: String): String = {
    val data = Numeric.hexStringToByteArray(message)
    val prefix = s"\u0019Ethereum Signed Message:\n${data.length}"
    val messageToSign = prefix.getBytes() ++ data
    applyOnAccountView { nodeView =>
      getFittingSecret(nodeView.vault, nodeView.state, Some(sender), BigInteger.ZERO)
        .map(secret => secret.sign(messageToSign))
        .map(signature => Numeric.toHexString(signature.getR ++ signature.getS ++ signature.getV))
        .orNull
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
    val signature = secret.sign(tx.messageToSign())
    new EthereumTransaction(tx, new SignatureSecp256k1(signature.getV, signature.getR, signature.getS))
  }

  private def binarySearch(lowBound: BigInteger, highBound: BigInteger)(fun: BigInteger => Boolean): BigInteger = {
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
          doCall(nodeView, params, tag)
          (true, None)
        } catch {
          case err: ExecutionRevertedException => (false, Some(err))
          case _: ExecutionFailedException => (false, None)
          case _: IntrinsicGasException => (false, None)
        }
      // Execute the binary search and hone in on an executable gas limit
      // We need to do a search because the gas required during execution is not necessarily equal to the consumed
      // gas after the execution. See https://github.com/ethereum/go-ethereum/commit/682875adff760a29a2bb0024190883e4b4dd5d72
      val requiredGasLimit = binarySearch(lowBound, highBound)(check(_)._1)
      // Reject the transaction as invalid if it still fails at the highest allowance
      if (requiredGasLimit == highBound) {
        val (success, reverted) = check(highBound)
        if (!success) {
          val error = reverted
            .map(err => RpcError.fromCode(RpcCode.ExecutionReverted, Numeric.toHexString(err.revertReason)))
            .getOrElse(RpcError.fromCode(RpcCode.InvalidParams, s"gas required exceeds allowance ($highBound)"))
          throw new RpcException(error)
        }
      }
      new Quantity(requiredGasLimit)
    }
  }

  @RpcMethod("eth_blockNumber")
  def blockNumber: Quantity = applyOnAccountView { nodeView =>
    new Quantity(nodeView.history.getCurrentHeight)
  }

  @RpcMethod("eth_chainId")
  def chainId: Quantity = new Quantity(networkParams.chainId)

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

  private def getBlockById(nodeView: NV, blockId: ModifierId): (AccountBlock, SidechainBlockInfo) = {
    val block = nodeView.history
      .getStorageBlockById(blockId)
      .getOrElse(throw new RpcException(RpcError.fromCode(RpcCode.UnknownBlock, "Invalid block tag parameter.")))
    val blockInfo = nodeView.history.blockInfoById(blockId)
    (block, blockInfo)
  }

  private def getBlockByTag(nodeView: NV, tag: String): (AccountBlock, SidechainBlockInfo) = {
    val blockId = getBlockIdByTag(nodeView, tag)
    val (block, blockInfo) = getBlockById(nodeView, blockId)
    (block, blockInfo)
  }

  private def getStateViewAtTag[A](nodeView: NV, tag: String)(fun: (AccountStateView, BlockContext) ⇒ A): A = {
    val (block, blockInfo) = getBlockByTag(nodeView, tag)
    val blockContext = new BlockContext(
      block.header,
      blockInfo.height,
      TimeToEpochUtils.timeStampToEpochNumber(networkParams, blockInfo.timestamp),
      blockInfo.withdrawalEpochInfo.epoch,
      networkParams.chainId
    )
    using(nodeView.state.getStateDbViewFromRoot(block.header.stateRoot))(fun(_, blockContext))
  }

  private def parseBlockTag(nodeView: NV, tag: String): Int = {
    tag match {
      case "earliest" => 1
      case "finalized" | "safe" => throw new RpcException(RpcError.fromCode(RpcCode.UnknownBlock))
      case "latest" | "pending" | null => nodeView.history.getCurrentHeight
      case height =>
        Try
          .apply(Numeric.decodeQuantity(height).intValueExact())
          .filter(_ <= nodeView.history.getCurrentHeight)
          .getOrElse(throw new RpcException(new RpcError(RpcCode.InvalidParams, "Invalid block tag parameter", null)))
    }
  }

  private def getBlockIdByTag(nodeView: NV, tag: String): ModifierId = {
    ModifierId(nodeView.history.blockIdByHeight(parseBlockTag(nodeView, tag)).get)
  }

  @RpcMethod("net_version")
  def version: String = String.valueOf(networkParams.chainId)

  @RpcMethod("eth_gasPrice")
  def gasPrice: Quantity = {
    applyOnAccountView { nodeView =>
      getStateViewAtTag(nodeView, "latest") { (_, blockContext) => new Quantity(blockContext.baseFee) }
    }
  }

  private def getTransactionAndReceipt(transactionHash: Hash) = {
    applyOnAccountView { nodeView =>
      using(nodeView.state.getView) { stateView =>
        stateView
          .getTransactionReceipt(transactionHash.toBytes)
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
  def getTransactionByHash(transactionHash: Hash): EthereumTransactionView = {
    getTransactionAndReceipt(transactionHash).map { case (block, tx, receipt) =>
      new EthereumTransactionView(receipt, tx, block.header.baseFee)
    }.orNull
  }

  @RpcMethod("eth_getTransactionByBlockHashAndIndex")
  def getTransactionByBlockHashAndIndex(hash: Hash, index: Quantity): EthereumTransactionView = {
    blockTransactionByIndex(_ => bytesToId(hash.toBytes), index)
  }

  @RpcMethod("eth_getTransactionByBlockNumberAndIndex")
  def getTransactionByBlockNumberAndIndex(tag: String, index: Quantity): EthereumTransactionView = {
    blockTransactionByIndex(nodeView => getBlockIdByTag(nodeView, tag), index)
  }

  private def blockTransactionByIndex(getBlockId: NV => ModifierId, index: Quantity): EthereumTransactionView = {
    val txIndex = index.toNumber.intValueExact()
    applyOnAccountView { nodeView =>
      nodeView.history
        .getStorageBlockById(getBlockId(nodeView))
        .flatMap(block => {
          block.transactions
            .drop(txIndex)
            .headOption
            .map(_.asInstanceOf[EthereumTransaction])
            .flatMap(tx =>
              using(nodeView.state.getView)(_.getTransactionReceipt(Numeric.hexStringToByteArray(tx.id)))
                .map(new EthereumTransactionView(_, tx, block.header.baseFee))
            )
        })
    }.orNull
  }

  @RpcMethod("eth_getTransactionReceipt")
  def getTransactionReceipt(transactionHash: Hash): EthereumReceiptView = {
    getTransactionAndReceipt(transactionHash).map { case (block, tx, receipt) =>
      // count the number of logs in the block before this transaction
      val firstLogIndex = applyOnAccountView { nodeView =>
        using(nodeView.state.getView) { stateView =>
          block.sidechainTransactions
            .take(receipt.transactionIndex)
            .map(_.id.toBytes)
            .flatMap(stateView.getTransactionReceipt)
            .map(_.consensusDataReceipt.logs.length)
            .sum
        }
      }
      new EthereumReceiptView(receipt, tx, block.header.baseFee, firstLogIndex)
    }.orNull
  }

  @RpcMethod("eth_sendRawTransaction")
  def sendRawTransaction(signedTxData: String): String = {
    val tx = EthereumTransactionDecoder.decode(signedTxData)
    implicit val timeout: Timeout = new Timeout(5, SECONDS)
    // submit tx to sidechain transaction actor
    val submit = (sidechainTransactionActorRef ? BroadcastTransaction(tx)).asInstanceOf[Future[Future[ModifierId]]]
    // wait for submit
    val validate = Await.result(submit, timeout.duration)
    // wait for validation of the transaction
    val txHash = Await.result(validate, timeout.duration)
    Numeric.prependHexPrefix(txHash)
  }

  @RpcMethod("eth_getCode")
  @RpcOptionalParameters(1)
  def getCode(address: Address, tag: String): String = {
    applyOnAccountView { nodeView =>
      getStateViewAtTag(nodeView, tag) { (tagStateView, _) =>
        val code = Option.apply(tagStateView.getCode(address.toBytes)).getOrElse(Array.emptyByteArray)
        Numeric.toHexString(code)
      }
    }
  }

  // Uncle Blocks RPCs
  // An uncle block is a block that did not get mined onto the canonical chain.
  // Only one block can be mined and acknowledged as canonical on the blockchain. The remaining blocks are uncle blocks.
  // The Sidechains that are created using this sdk DO NOT support uncle blocks.
  // For this reason:
  // - eth_getUncleCountByBlockHash and eth_getUncleCountByBlockNumber always return 0x0
  // - eth_getUncleByBlockHashAndIndex and eth_getUncleByBlockNumberAndIndex always return null (as no block was found)
  @RpcMethod("eth_getUncleCountByBlockHash")
  def getUncleCountByBlockHash(hash: Hash) = new Quantity(0L)

  @RpcMethod("eth_getUncleCountByBlockNumber")
  def eth_getUncleCountByBlockNumber(tag: String) = new Quantity(0L)

  @RpcMethod("eth_getUncleByBlockHashAndIndex")
  def eth_getUncleByBlockHashAndIndex(hash: Hash, index: Quantity): Null = null

  @RpcMethod("eth_getUncleByBlockNumberAndIndex")
  def eth_getUncleByBlockNumberAndIndex(tag: String, index: Quantity): Null = null

  // Eth Syncing RPC
  @RpcMethod("eth_syncing")
  def eth_syncing() = false

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

  @RpcMethod("debug_traceBlockByHash")
  @RpcOptionalParameters(1)
  def traceBlockByHash(hash: Hash, traceParams: TraceParams): DebugTraceBlockView = {
    applyOnAccountView { nodeView =>
      // get block to trace
      val (block, blockInfo) = getBlockById(nodeView, bytesToId(hash.toBytes))

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
  def traceTransaction(transactionHash: Hash, traceParams: TraceParams): DebugTraceTransactionView = {
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

  @RpcMethod("zen_getForwardTransfers")
  def getForwardTransfers(blockId: String): ForwardTransfersView = {
    if (blockId == null) return null
    applyOnAccountView { nodeView =>
      nodeView.history
        .getStorageBlockById(getBlockIdByTag(nodeView, blockId))
        .map(block => new ForwardTransfersView(getForwardTransfersForBlock(block).asJava, false))
        .orNull
    }
  }

  @RpcMethod("eth_getStorageAt")
  @RpcOptionalParameters(1)
  def getStorageAt(address: Address, key: Quantity, tag: String): Hash = {
    val storageKey = Numeric.toBytesPadded(key.toNumber, 32)
    applyOnAccountView { nodeView =>
      getStateViewAtTag(nodeView, tag) { (stateView, _) =>
        Hash.FromBytes(stateView.getAccountStorage(address.toBytes, storageKey))
      }
    }
  }

  @RpcMethod("eth_getProof")
  @RpcOptionalParameters(1)
  def getProof(address: Address, keys: Array[Quantity], tag: String): EthereumAccountProofView = {
    val storageKeys = keys.map(key => Numeric.toBytesPadded(key.toNumber, 32))
    applyOnAccountView { nodeView =>
      getStateViewAtTag(nodeView, tag) { (stateView, _) =>
        new EthereumAccountProofView(stateView.getProof(address.toBytes, storageKeys))
      }
    }
  }

  @RpcMethod("zen_getFeePayments")
  def getFeePayments(blockId: String): AccountFeePaymentsInfo = {
    if (blockId == null) {
      return null
    }

    var feePayments: AccountFeePaymentsInfo = null

    applyOnAccountView { nodeView =>
      nodeView.history
        .getFeePaymentsInfo(blockId)
        .ifPresent(p => feePayments = p)
    }

    feePayments
  }

  @RpcMethod("eth_accounts")
  def getAccounts: Array[String] = {
    applyOnAccountView { nodeView =>
      nodeView.vault
        .secretsOfType(classOf[PrivateKeySecp256k1])
        .map(_.asInstanceOf[PrivateKeySecp256k1])
        .map(_.publicImage())
        .map(_.checksumAddress())
        .toArray
    }
  }

  @RpcMethod("eth_feeHistory")
  @RpcOptionalParameters(1)
  def feeHistory(
      blockCount: Quantity,
      newestBlock: String,
      rewardPercentiles: Array[Double]
  ): EthereumFeeHistoryView = {
    val percentiles = sanitizePercentiles(rewardPercentiles)
    applyOnAccountView { nodeView =>
      val (requestedBlock, requestedBlockInfo) = getBlockByTag(nodeView, newestBlock)
      // limit the range of blocks by the number of available blocks and cap at 1024
      val blocks = blockCount.toNumber.intValueExact().min(requestedBlockInfo.height).min(1024)
      // geth comment: returning with no data and no error means there are no retrievable blocks
      if (blocks < 1) return new EthereumFeeHistoryView()
      // calculate block number of the "oldest" block in the range
      val oldestBlock = requestedBlockInfo.height + 1 - blocks

      // include the calculated base fee of the next block after the requested range
      val baseFeePerGas = new Array[BigInteger](blocks + 1)
      val gasUsedRatio = new Array[Double](blocks)
      val reward = if (percentiles.nonEmpty) new Array[Array[BigInteger]](blocks) else null

      using(nodeView.state.getView) { stateView =>
        for (i <- 0 until blocks) {
          val block = nodeView.history
            .blockIdByHeight(oldestBlock + i)
            .map(ModifierId(_))
            .flatMap(nodeView.history.getStorageBlockById)
            .get
          baseFeePerGas(i) = block.header.baseFee
          gasUsedRatio(i) = block.header.gasUsed.toDouble / block.header.gasLimit.toDouble
          if (percentiles.nonEmpty) reward(i) = getRewardsForBlock(block, stateView, percentiles)
        }
      }
      // calculate baseFee for the next block after the requested range
      baseFeePerGas(blocks) = calculateNextBaseFee(requestedBlock)

      new EthereumFeeHistoryView(oldestBlock, baseFeePerGas, gasUsedRatio, reward)
    }
  }

  private def sanitizePercentiles(percentiles: Array[Double]): Array[Double] = {
    if (percentiles == null || percentiles.isEmpty) return Array.emptyDoubleArray
    // verify that percentiles are within [0,100]
    percentiles
      .find(p => p < 0 || p > 100)
      .map(p => throw new RpcException(new RpcError(RpcCode.InvalidParams, s"invalid reward percentile: $p", null)))
    // verify that percentiles are monotonically increasing
    percentiles
      .sliding(2)
      .filter(p => p.head > p.last)
      .map(p =>
        throw new RpcException(
          new RpcError(RpcCode.InvalidParams, s"invalid reward percentile: ${p.head} > ${p.last}", null)
        )
      )
    percentiles
  }

  private def getRewardsForBlock(
      block: AccountBlock,
      stateView: AccountStateView,
      percentiles: Array[Double]
  ): Array[BigInteger] = {
    val txs = block.transactions.map(_.asInstanceOf[EthereumTransaction])
    // return an all zero row if there are no transactions to gather data from
    if (txs.isEmpty) return percentiles.map(_ => BigInteger.ZERO)

    // collect gas used and reward (effective gas tip) per transaction, sorted ascending by reward
    case class GasAndReward(gasUsed: Long, reward: BigInteger)
    val sortedRewards = txs
      .map(tx =>
        GasAndReward(
          stateView.getTransactionReceipt(Numeric.hexStringToByteArray(tx.id)).get.gasUsed.longValueExact(),
          getEffectiveGasTip(tx, block.header.baseFee)
        )
      )
      .sortBy(_.reward)
      .iterator

    var current = sortedRewards.next()
    var sumGasUsed = current.gasUsed
    val rewards = new Array[BigInteger](percentiles.length)
    for (i <- percentiles.indices) {
      val thresholdGasUsed = (block.header.gasUsed.toDouble * percentiles(i) / 100).toLong
      // continue summation as long as the total is below the percentile threshold
      while (sumGasUsed < thresholdGasUsed && sortedRewards.hasNext) {
        current = sortedRewards.next()
        sumGasUsed += current.gasUsed
      }
      rewards(i) = current.reward
    }
    rewards
  }

  private def getEffectiveGasTip(tx: EthereumTransaction, baseFee: BigInteger): BigInteger = {
    if (baseFee == null) tx.getMaxPriorityFeePerGas
    // we do not need to check if MaxFeePerGas is higher than baseFee, because the tx is already included in the block
    else tx.getMaxPriorityFeePerGas.min(tx.getMaxFeePerGas.subtract(baseFee))
  }

  @RpcMethod("eth_getLogs")
  def getLogs(query: FilterQuery): Seq[EthereumLogView] = {
    query.sanitize()
    applyOnAccountView { nodeView =>
      using(nodeView.state.getView) { stateView =>
        if (query.blockHash != null) {
          // we currently need to get the block by blockhash and then retrieve the receipt for each tx via tx-hash
          // geth retrieves all logs of a block by blockhash
          val block = nodeView.history
            .getStorageBlockById(bytesToId(query.blockHash.toBytes))
            .getOrElse(throw new RpcException(RpcError.fromCode(RpcCode.UnknownBlock, "invalid block hash")))
          getBlockLogs(stateView, block, query)
        } else {
          val start = parseBlockTag(nodeView, query.fromBlock)
          val end = parseBlockTag(nodeView, query.toBlock)
          if (start > end) {
            throw new RpcException(RpcError.fromCode(RpcCode.InvalidParams, "invalid block range"))
          }
          // get the logs from all blocks in the range into one flat list
          (start to end).flatMap(blockNumber => {
            nodeView.history
              .blockIdByHeight(blockNumber)
              .map(ModifierId(_))
              .flatMap(nodeView.history.getStorageBlockById)
              .map(getBlockLogs(stateView, _, query))
              .get
          })
        }
      }
    }
  }

  /**
   * Get all logs of a block matching the given query. Replication of the original implementation in GETH, see:
   * github.com/ethereum/go-ethereum@v1.10.26/eth/filters/filter.go:227
   */
  private def getBlockLogs(stateView: AccountStateView, block: AccountBlock, query: FilterQuery): Seq[EthereumLogView] = {
    val filtered = query.address.length > 0 || query.topics.length > 0
    if (filtered && !testBloom(block.header.logsBloom, query.address, query.topics)) {
        // bail out if address or topic queries are given, but they fail the bloom filter test
        return Seq.empty
    }
    // retrieve all logs of the given block
    var logIndex = 0
    val logs = block.sidechainTransactions
      .map(_.id.toBytes)
      .flatMap(stateView.getTransactionReceipt)
      .flatMap(receipt =>
        receipt.consensusDataReceipt.logs.map(log => {
          val logView = new EthereumLogView(receipt, log, logIndex)
          logIndex += 1
          logView
        })
      )
    if (filtered) {
      // return filtered logs
      logs.filter(testLog(query.address, query.topics))
    } else {
      // return all logs
      logs
    }
  }

  /**
   * Tests if a bloom filter matches the given address and topic queries. Replication of the original implementation
   * in GETH, see: github.com/ethereum/go-ethereum@v1.10.26/eth/filters/filter.go:328
   */
  private def testBloom(bloom: Bloom, addresses: Array[Address], topics: Array[Array[Hash]]): Boolean = {
    // bail out if an address filter is given and none of the addresses are contained in the bloom filter
    if (addresses.length > 0 && !addresses.map(_.toBytes).exists(bloom.test)) {
      false
    } else {
      topics.forall(sub => {
        // empty rule set == wildcard, otherwise test if at least one of the given topics is contained
        sub.length == 0 || sub.map(_.toBytes).exists(bloom.test)
      })
    }
  }

  /**
   * Tests if a log matches the given address and topic queries. Replication of the original implementation in
   * GETH, see: github.com/ethereum/go-ethereum@v1.10.26/eth/filters/filter.go:293
   */
  private def testLog(addresses: Array[Address], topics: Array[Array[Hash]])(log: EthereumLogView): Boolean = {
    if (addresses.length > 0 && addresses.map(_.toString).contains(log.address)) return false
    // skip if the number of filtered topics is greater than the amount of topics in the log
    if (topics.length > log.topics.length) return false
    topics.zip(log.topics).forall({ case (sub, topic) => sub.length == 0 || sub.map(_.toString).contains(topic)})
  }
}
