package com.horizen.account.api.rpc.service

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.fasterxml.jackson.databind.JsonNode
import com.horizen.account.api.rpc.handler.RpcException
import com.horizen.account.api.rpc.types._
import com.horizen.account.api.rpc.utils._
import com.horizen.account.block.AccountBlock
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.forger.AccountForgeMessageBuilder
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.{AccountMemoryPool, MempoolMap}
import com.horizen.account.proof.SignatureSecp256k1
import com.horizen.account.receipt.{Bloom, EthereumReceipt}
import com.horizen.account.secret.PrivateKeySecp256k1
import com.horizen.account.state._
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.AccountForwardTransfersHelper.getForwardTransfersForBlock
import com.horizen.account.utils.FeeUtils.{INITIAL_BASE_FEE, calculateNextBaseFee}
import com.horizen.account.utils.Secp256k1.generateContractAddress
import com.horizen.account.utils.{BigIntegerUtil, EthereumTransactionDecoder, FeeUtils}
import com.horizen.account.wallet.AccountWallet
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.chain.SidechainBlockInfo
import com.horizen.forge.MainchainSynchronizer
import com.horizen.params.NetworkParams
import com.horizen.transaction.exception.TransactionSemanticValidityException
import com.horizen.utils.{BytesUtils, ClosableResourceHandler, TimeToEpochUtils}
import com.horizen.{EthServiceSettings, SidechainTypes}
import io.horizen.evm.results.ProofAccountResult
import io.horizen.evm.{Address, Hash, TraceOptions}
import org.web3j.utils.Numeric
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.consensus.ModifierSemanticValidity
import sparkz.core.network.ConnectedPeer
import sparkz.core.network.NetworkController.ReceivableMessages.GetConnectedPeers
import sparkz.core.{NodeViewHolder, bytesToId, idToBytes}
import sparkz.util.{ModifierId, SparkzLogging}

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.concurrent.TrieMap
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class EthService(
    scNodeViewHolderRef: ActorRef,
    networkControllerRef: ActorRef,
    nvtimeout: FiniteDuration,
    networkParams: NetworkParams,
    settings: EthServiceSettings,
    maxIncomingConnections: Int,
    rpcClientVersion: String,
    sidechainTransactionActorRef: ActorRef,
    transactionsCompanion: SidechainAccountTransactionsCompanion
) extends RpcService
      with ClosableResourceHandler
      with SparkzLogging {
  type NV = CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool]
  implicit val timeout: Timeout = new Timeout(nvtimeout)

  private def applyOnAccountView[R](functionToBeApplied: NV => R): R = {
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

  @RpcMethod("txpool_status")
  def txpoolStatus(): TxPoolStatus = applyOnAccountView { nodeView =>
    new TxPoolStatus(
      nodeView.pool.getExecutableTransactions.size(),
      nodeView.pool.getNonExecutableTransactions.size()
    )
  }

  private def convertMempoolMap(poolMap: TrieMap[SidechainTypes#SCP, MempoolMap#TxByNonceMap]) =
    CollectionConverters.mapAsJavaMap(
      poolMap.map { case (proposition, txByNonce) =>
        proposition.asInstanceOf[AddressProposition].address() -> CollectionConverters.mapAsJavaMap(
          txByNonce.mapValues(tx => new TxPoolTransaction(tx.asInstanceOf[EthereumTransaction]))
        )
      }
    )

  private def convertMempoolMapInspect(poolMap: TrieMap[SidechainTypes#SCP, MempoolMap#TxByNonceMap]) =
    CollectionConverters.mapAsJavaMap(
      poolMap.map { case (proposition, txByNonce) =>
        proposition.asInstanceOf[AddressProposition].address() -> CollectionConverters.mapAsJavaMap(
          txByNonce.mapValues(tx => {
            val txPoolTx = new TxPoolTransaction(tx.asInstanceOf[EthereumTransaction])
            // check to address if null it is a contract creation transaction
            if(txPoolTx.to!=null) {
              s"${txPoolTx.to.toString}: ${txPoolTx.value} wei + ${txPoolTx.gas} gas × ${txPoolTx.gasPrice} wei"
            } else {
              s"contract creation: ${txPoolTx.value} wei + ${txPoolTx.gas} gas × ${txPoolTx.gasPrice} wei"
            }
          })
        )
      }
    )

  @RpcMethod("txpool_content")
  def txpoolContent(): TxPoolContent = applyOnAccountView { nodeView =>
    new TxPoolContent(
      convertMempoolMap(nodeView.pool.getExecutableTransactionsMap),
      convertMempoolMap(nodeView.pool.getNonExecutableTransactionsMap)
    )
  }

  @RpcMethod("txpool_contentFrom")
  def txpoolContent(from: Address): TxPoolContent = applyOnAccountView { nodeView =>
    new TxPoolContent(
      convertMempoolMap(nodeView.pool.getExecutableTransactionsMapFrom(from)),
      convertMempoolMap(nodeView.pool.getNonExecutableTransactionsMapFrom(from))
    )
  }

  @RpcMethod("txpool_inspect")
  def txpoolInspect(): TxPoolInspect = applyOnAccountView { nodeView =>
    new TxPoolInspect(
      convertMempoolMapInspect(nodeView.pool.getExecutableTransactionsMap),
      convertMempoolMapInspect(nodeView.pool.getNonExecutableTransactionsMap)
    )
  }

  @RpcMethod("eth_getBlockByNumber")
  def getBlockByNumber(tag: String, hydratedTx: Boolean): EthereumBlockView = {
    applyOnAccountView { nodeView =>
      try {
        constructEthBlockWithTransactions(nodeView, getBlockIdByTag(nodeView, tag), hydratedTx)
      } catch {
        case _: BlockNotFoundException => null
      }
    }
  }

  @RpcMethod("eth_getBlockByHash")
  def getBlockByHash(hash: Hash, hydratedTx: Boolean): EthereumBlockView = {
    applyOnAccountView { nodeView =>
      try {
        constructEthBlockWithTransactions(nodeView, bytesToId(hash.toBytes), hydratedTx)
      } catch {
        case _: BlockNotFoundException => null
      }
    }
  }

  private def constructEthBlockWithTransactions(
      nodeView: NV,
      blockId: ModifierId,
      hydratedTx: Boolean
  ): EthereumBlockView = {
    val (block, blockInfo): (AccountBlock, SidechainBlockInfo) = getBlockById(nodeView, blockId)
    val history = nodeView.history
    val (blockNumber, blockHash, view): (Long, Hash, AccountStateView) =
      if (blockId == null) {
        (history.getCurrentHeight + 1, null, getPendingStateView(nodeView, block, blockInfo))
      } else {
        (
          history.getBlockHeightById(blockId).get().toLong,
          new Hash(blockId.toBytes),
          nodeView.state.getView
        )
      }

    using(view) { stateView =>
      if (hydratedTx) {
        val receipts = block.transactions.map(_.id.toBytes).flatMap(stateView.getTransactionReceipt)
        EthereumBlockView.hydrated(blockNumber, blockHash, block, receipts.asJava)
      } else {
        EthereumBlockView.notHydrated(blockNumber, blockHash, block)
      }
    }
  }

  @RpcMethod("eth_getBlockTransactionCountByHash")
  def getBlockTransactionCountByHash(hash: Hash): BigInteger = {
    blockTransactionCount(_ => bytesToId(hash.toBytes))
  }

  @RpcMethod("eth_getBlockTransactionCountByNumber")
  def getBlockTransactionCountByNumber(tag: String): BigInteger = {
    blockTransactionCount(nodeView => getBlockIdByTag(nodeView, tag))
  }

  private def blockTransactionCount(getBlockId: NV => ModifierId): BigInteger = {
    applyOnAccountView { nodeView =>
      try {
        val blockId = getBlockId(nodeView)
        if (blockId == null) {
          BigInteger.valueOf(nodeView.pool.getExecutableTransactionsMap.map(_._2.size).sum)
        } else {
          nodeView.history.getStorageBlockById(blockId)
            .map(block => BigInteger.valueOf(block.transactions.size))
            .orNull
        }
      } catch {
        case _: BlockNotFoundException => null
      }
    }
  }

  private def doCall(nodeView: NV, params: TransactionArgs, tag: String): Array[Byte] = {
    getStateViewAtTag(nodeView, tag) { (tagStateView, blockContext) =>
      val msg = params.toMessage(blockContext.baseFee, settings.globalRpcGasCap)
      tagStateView.applyMessage(msg, new GasPool(msg.getGasLimit), blockContext)
    }
  }

  @RpcMethod("eth_call")
  @RpcOptionalParameters(1)
  def call(params: TransactionArgs, tag: String): Array[Byte] = applyOnAccountView(doCall(_, params, tag))

  @RpcMethod("eth_sendTransaction")
  def sendTransaction(params: TransactionArgs): Hash = {
    val tx = signTransaction(params)
    sendRawTransaction(tx)
  }

  @RpcMethod("eth_signTransaction")
  def signTransaction(params: TransactionArgs): Array[Byte] = {
    applyOnAccountView { nodeView =>
      val unsignedTx =
        params.toTransaction(
          networkParams,
          nodeView.history,
          nodeView.pool,
          () => doEstimateGas(nodeView, params, "pending")
        )
      val secret = getFittingSecret(nodeView.vault, nodeView.state, params.getFrom, unsignedTx.maxCost())
      val signedTx = signTransactionWithSecret(secret, unsignedTx)
      signedTx.encode(true)
    }
  }

  /**
   * Sign calculates an ECDSA signature for: keccack256("\x19Ethereum Signed Message:\n" + len(message) + message). This
   * gives context to the signed message and prevents signing of transactions.
   */
  @RpcMethod("eth_sign")
  def sign(sender: Address, message: Array[Byte]): Array[Byte] = {
    val prefix = s"\u0019Ethereum Signed Message:\n${message.length}"
    val messageToSign = prefix.getBytes(StandardCharsets.UTF_8) ++ message
    applyOnAccountView { nodeView =>
      val secret = getFittingSecret(nodeView.vault, nodeView.state, sender, BigInteger.ZERO)
      val signature = secret.sign(messageToSign)
      signature.getR ++ signature.getS ++ signature.getV
    }
  }

  private def getFittingSecret(
      wallet: AccountWallet,
      state: AccountState,
      sender: Address,
      minimumBalance: BigInteger
  ): PrivateKeySecp256k1 = {
    val matchingKeys = wallet
      .secretsOfType(classOf[PrivateKeySecp256k1])
      .map(_.asInstanceOf[PrivateKeySecp256k1])
      .filter(_.publicImage.address.equals(sender))
    if (matchingKeys.isEmpty) {
      throw new RpcException(RpcError.fromCode(RpcCode.InvalidParams, "no matching key for given sender"))
    }
    matchingKeys.find(secret => state.getBalance(secret.publicImage.address).compareTo(minimumBalance) >= 0)
      .getOrElse(throw new RpcException(RpcError.fromCode(RpcCode.InvalidParams, "insufficient funds")))
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

  private def doEstimateGas(nodeView: NV, params: TransactionArgs, tag: String): BigInteger = {
    // Binary search the gas requirement, as it may be higher than the amount used
    val lowBound = GasUtil.TxGas.subtract(BigInteger.ONE)
    // Determine the highest gas limit can be used during the estimation.
    var highBound = params.gas
    getStateViewAtTag(nodeView, tag) { (tagStateView, blockContext) =>
      if (highBound == null || highBound.compareTo(GasUtil.TxGas) < 0) {
        highBound = blockContext.blockGasLimit
      }
      // Normalize the max fee per gas the call is willing to spend.
      val feeCap = if (params.gasPrice != null) {
        params.gasPrice
      } else if (params.maxFeePerGas != null) {
        params.maxFeePerGas
      } else {
        BigInteger.ZERO
      }
      // Recap the highest gas limit with account's available balance.
      if (feeCap.bitLength() > 0) {
        val balance = tagStateView.getBalance(params.getFrom)
        if (params.value.compareTo(balance) >= 0)
          throw new RpcException(RpcError.fromCode(RpcCode.InvalidParams, "insufficient funds for transfer"))
        val allowance = balance.subtract(params.value).divide(feeCap)
        if (highBound.compareTo(allowance) > 0) {
          highBound = allowance
        }
      }
    }
    if (highBound.compareTo(settings.globalRpcGasCap) > 0) {
      highBound = settings.globalRpcGasCap
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
    requiredGasLimit
  }

  @RpcMethod("eth_estimateGas")
  @RpcOptionalParameters(1)
  def estimateGas(params: TransactionArgs, tag: String): BigInteger = {
    applyOnAccountView { nodeView =>
      doEstimateGas(nodeView, params, tag)
    }
  }

  @RpcMethod("eth_blockNumber")
  def blockNumber: BigInteger = applyOnAccountView { nodeView =>
    BigInteger.valueOf(nodeView.history.getCurrentHeight)
  }

  @RpcMethod("eth_chainId")
  def chainId: BigInteger = BigInteger.valueOf(networkParams.chainId)

  @RpcMethod("eth_getBalance")
  @RpcOptionalParameters(1)
  def getBalance(address: Address, tag: String): BigInteger = {
    applyOnAccountView { nodeView =>
      try {
        getStateViewAtTag(nodeView, tag) { (tagStateView, _) =>
          tagStateView.getBalance(address)
        }
      } catch {
        case _: BlockNotFoundException => BigInteger.ZERO
      }
    }
  }

  @RpcMethod("eth_getTransactionCount")
  @RpcOptionalParameters(1)
  def getTransactionCount(address: Address, tag: String): BigInteger = {
    applyOnAccountView { nodeView =>
      try {
        getStateViewAtTag(nodeView, tag) { (tagStateView, _) =>
          tagStateView.getNonce(address)
        }
      } catch {
        case _: BlockNotFoundException => BigInteger.ZERO
      }
    }
  }

  /**
   * Returns tuple of AccountBlock and SidechainBlockInfo for given blockId blockId = null is a valid case, returning
   * pending block and its block info Throws RpcException for not found blockId or errors while creating pending block
   */
  private def getBlockById(nodeView: NV, blockId: ModifierId): (AccountBlock, SidechainBlockInfo) = {
    val (block, blockInfo) = if (blockId == null) {
      val pendingBlock = getPendingBlock(nodeView).getOrElse(throw BlockNotFoundException())
      (
        pendingBlock,
        getPendingBlockInfo(nodeView)
      )
    } else {
      (
        nodeView.history
          .getStorageBlockById(blockId)
          .getOrElse(throw BlockNotFoundException()),
        nodeView.history.blockInfoById(blockId)
      )
    }
    (block, blockInfo)
  }

  /**
   * Returns a SidechainBlockInfo for a given blockId
   * blockId = null is a valid case, returning pending block info
   * Throws RpcException for not found blockId or errors while creating pending block
   */
  private def getBlockInfoById(nodeView: NV, blockId: ModifierId): SidechainBlockInfo = {
    val blockInfo = if (blockId == null) {
      getPendingBlockInfo(nodeView)
    } else {
      nodeView.history.blockInfoById(blockId)
    }
    blockInfo
  }

  private def getBlockByTag(nodeView: NV, tag: String): (AccountBlock, SidechainBlockInfo) = {
    val blockId = getBlockIdByTag(nodeView, tag)
    val (block, blockInfo) = getBlockById(nodeView, blockId)
    (block, blockInfo)
  }

  private def getBlockContext(
      block: AccountBlock,
      blockInfo: SidechainBlockInfo,
      blockHashProvider: HistoryBlockHashProvider
  ): BlockContext = {
    new BlockContext(
      block.header,
      blockInfo.height,
      TimeToEpochUtils.timeStampToEpochNumber(networkParams, blockInfo.timestamp),
      blockInfo.withdrawalEpochInfo.epoch,
      networkParams.chainId,
      blockHashProvider
    )
  }

  private def getStateViewAtTag[A](nodeView: NV, tag: String)(fun: (StateDbAccountStateView, BlockContext) => A): A = {
    val (block, blockInfo) = getBlockByTag(nodeView, tag)
    val blockContext = getBlockContext(block, blockInfo, nodeView.history)
    if (tag == "pending") {
      using(getPendingStateView(nodeView, block, blockInfo))(fun(_, blockContext))
    } else {
      using(nodeView.state.getStateDbViewFromRoot(block.header.stateRoot))(fun(_, blockContext))
    }
  }

  private def parseBlockTag(nodeView: NV, tag: String): Int = {
    tag match {
      case "earliest" => 1
      case "finalized" | "safe" => nodeView.history.getCurrentHeight match {
          case height if height <= 100 => throw BlockNotFoundException()
          case height => height - 100
        }
      case "latest" | null => nodeView.history.getCurrentHeight
      case "pending" => nodeView.history.getCurrentHeight + 1
      case height => parseBlockNumber(height)
          .getOrElse(throw new RpcException(RpcError.fromCode(RpcCode.UnknownBlock, "invalid block number or tag")))
    }
  }

  private def parseBlockNumber(number: String): Option[Int] = {
    Try(Numeric.decodeQuantity(number).intValueExact()).toOption
  }

  private def getBlockIdByTag(nodeView: NV, tag: String): ModifierId = {
    val blockId = parseBlockTag(nodeView, tag) match {
      case height if height == nodeView.history.getCurrentHeight + 1 => null
      case height => ModifierId(nodeView.history.blockIdByHeight(height).getOrElse(throw BlockNotFoundException()))
    }
    blockId
  }

  private def getBlockIdByHash(tag: String): Option[ModifierId] = {
    if (tag.length == 66 && tag.substring(0, 2) == "0x")
      Some(bytesToId(BytesUtils.fromHexString(tag.substring(2))))
    else
      None
  }

  private def getBlockIdByHashOrTag(nodeView: NV, tag: String): ModifierId = {
    getBlockIdByHash(tag).getOrElse(getBlockIdByTag(nodeView, tag))
  }

  private def getBlockIdByHashOrNumber(nodeView: NV, blockHashOrNumber: String): ModifierId = {
    getBlockIdByHash(blockHashOrNumber).getOrElse(
      parseBlockNumber(blockHashOrNumber)
        .flatMap(nodeView.history.blockIdByHeight)
        .map(ModifierId(_))
        .getOrElse(throw new RpcException(new RpcError(RpcCode.InvalidParams, "Invalid block input parameter", null)))
    )
  }

  @RpcMethod("net_version")
  def version: String = String.valueOf(networkParams.chainId)

  private def getPeers: Seq[ConnectedPeer] = {
    val peerReq = (networkControllerRef ? GetConnectedPeers).asInstanceOf[Future[Seq[ConnectedPeer]]]
    Await.result(peerReq, nvtimeout)
  }

  @RpcMethod("net_peerCount")
  def peerCount: BigInteger = BigInteger.valueOf(getPeers.size)

  @RpcMethod("net_listening")
  def listening: Boolean = {
    val incomingConnections = getPeers.flatMap(_.peerInfo).count(_.connectionType.exists(_.isIncoming))
    incomingConnections < maxIncomingConnections
  }

  @RpcMethod("web3_clientVersion")
  def clientVersion: String = rpcClientVersion

  @RpcMethod("eth_gasPrice")
  def gasPrice: BigInteger = {
    applyOnAccountView { nodeView =>
      Backend.calculateGasPrice(nodeView.history, nodeView.history.bestBlock.header.baseFee)
    }
  }

  private def getTransactionAndReceipt(transactionHash: Hash)
      : Option[(AccountBlock, EthereumTransaction, EthereumReceipt)] = {
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
      new EthereumTransactionView(tx, receipt, block.header.baseFee)
    }.orNull
  }

  @RpcMethod("eth_getTransactionByBlockHashAndIndex")
  def getTransactionByBlockHashAndIndex(hash: Hash, index: BigInteger): EthereumTransactionView = {
    blockTransactionByIndex(_ => bytesToId(hash.toBytes), index)
  }

  @RpcMethod("eth_getTransactionByBlockNumberAndIndex")
  def getTransactionByBlockNumberAndIndex(tag: String, index: BigInteger): EthereumTransactionView = {
    blockTransactionByIndex(nodeView => getBlockIdByTag(nodeView, tag), index)
  }

  private def getPendingBlock(nodeView: NV): Option[AccountBlock] = {
    new AccountForgeMessageBuilder(new MainchainSynchronizer(null), transactionsCompanion, networkParams, false)
      .getPendingBlock(nodeView)
  }

  private def getPendingBlockInfo(nodeView: NV): SidechainBlockInfo = {
    val parentId = nodeView.history.bestBlockId
    val parentInfo = nodeView.history.blockInfoById(parentId)
    new SidechainBlockInfo(
      parentInfo.height + 1,
      parentInfo.score + 1,
      parentId,
      System.currentTimeMillis / 1000,
      ModifierSemanticValidity.Unknown,
      null,
      null,
      parentInfo.withdrawalEpochInfo,
      parentInfo.vrfOutputOpt, // same as for parent block, used as a source of BlockContext.random field
      parentInfo.lastBlockInPreviousConsensusEpoch
    )
  }

  private def getPendingStateView(
      nodeView: NV,
      block: AccountBlock,
      blockInfo: SidechainBlockInfo
  ): AccountStateView = {
    val pendingStateView = nodeView.state.getView

    // apply mainchain references
    for (mcBlockRefData <- block.mainchainBlockReferencesData) {
      pendingStateView.applyMainchainBlockReferenceData(mcBlockRefData)
    }

    val gasPool = new GasPool(block.header.gasLimit)

    val receiptList = new ListBuffer[EthereumReceipt]()
    var cumGasUsed: BigInteger = BigInteger.ZERO

    // apply transactions
    for ((tx, i) <- block.transactions.zipWithIndex) {
      pendingStateView.applyTransaction(tx, i, gasPool, getBlockContext(block, blockInfo, nodeView.history)) match {
        case Success(consensusDataReceipt) =>
          val txGasUsed = consensusDataReceipt.cumulativeGasUsed.subtract(cumGasUsed)

          // update cumulative gas used so far
          cumGasUsed = consensusDataReceipt.cumulativeGasUsed
          val ethTx = tx.asInstanceOf[EthereumTransaction]

          val txHash = BytesUtils.fromHexString(ethTx.id)

          // The contract address created, if the transaction was a contract creation
          val contractAddress = if (ethTx.getTo.isEmpty) {
            // this w3j util method is equivalent to the createAddress() in geth triggered also by CREATE opcode.
            // Note: geth has also a CREATE2 opcode which may be optionally used in a smart contract solidity implementation
            // in order to deploy another (deeper) smart contract with an address that is pre-determined before deploying it.
            // This does not impact our case since the CREATE2 result would not be part of the receipt.
            Option(generateContractAddress(ethTx.getFrom.address(), ethTx.getNonce))
          } else {
            // otherwise nothing
            None
          }

          // get a receipt obj with non consensus data (logs updated too)
          val fullReceipt =
            EthereumReceipt(
              consensusDataReceipt,
              txHash,
              i,
              null,
              nodeView.history.getCurrentHeight + 1,
              txGasUsed,
              contractAddress
            )

          receiptList += fullReceipt
      }
    }
    // update tx receipts
    pendingStateView.updateTransactionReceipts(receiptList)

    // update next base fee
    pendingStateView.updateNextBaseFee(FeeUtils.calculateNextBaseFee(block))

    pendingStateView
  }

  private def blockTransactionByIndex(getBlockId: NV => ModifierId, index: BigInteger): EthereumTransactionView = {
    val txIndex = index.intValueExact()
    applyOnAccountView { nodeView =>
      try {
        val blockId = getBlockId(nodeView)
        val (block, blockInfo): (AccountBlock, SidechainBlockInfo) = getBlockById(nodeView, blockId)
        val view = if (blockId == null) getPendingStateView(nodeView, block, blockInfo) else nodeView.state.getView
        block.transactions
          .drop(txIndex)
          .headOption
          .map(_.asInstanceOf[EthereumTransaction])
          .flatMap(tx =>
            using(view)(_.getTransactionReceipt(BytesUtils.fromHexString(tx.id)))
              .map(new EthereumTransactionView(tx, _, block.header.baseFee))
          ).orNull
      } catch {
        case _: BlockNotFoundException => null
      }
    }
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
  def sendRawTransaction(signedTxData: Array[Byte]): Hash = {
    val tx = EthereumTransactionDecoder.decode(signedTxData)
    // submit tx to sidechain transaction actor
    val submit = (sidechainTransactionActorRef ? BroadcastTransaction(tx)).asInstanceOf[Future[Future[ModifierId]]]
    // wait for submit
    val validate = Await.result(submit, timeout.duration)
    // wait for validation of the transaction
    val txHash = Await.result(validate, timeout.duration)
    new Hash(idToBytes(txHash))
  }

  @RpcMethod("eth_getCode")
  @RpcOptionalParameters(1)
  def getCode(address: Address, tag: String): Array[Byte] = {
    applyOnAccountView { nodeView =>
      try {
        getStateViewAtTag(nodeView, tag) { (tagStateView, _) =>
          Option.apply(tagStateView.getCode(address)).getOrElse(Array.emptyByteArray)
        }
      } catch {
        case _: BlockNotFoundException => null
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
  def getUncleCountByBlockHash(hash: Hash): BigInteger = BigInteger.ZERO

  @RpcMethod("eth_getUncleCountByBlockNumber")
  def eth_getUncleCountByBlockNumber(tag: String): BigInteger = BigInteger.ZERO

  @RpcMethod("eth_getUncleByBlockHashAndIndex")
  def eth_getUncleByBlockHashAndIndex(hash: Hash, index: BigInteger): Null = null

  @RpcMethod("eth_getUncleByBlockNumberAndIndex")
  def eth_getUncleByBlockNumberAndIndex(tag: String, index: BigInteger): Null = null

  @RpcMethod("eth_syncing")
  def eth_syncing() = false

  private def traceBlockById(nodeView: NV, blockId: ModifierId, config: TraceOptions): List[JsonNode] = {
    // get block to trace
    val (block, blockInfo) = getBlockById(nodeView, blockId)

    // get state at previous block
    getStateViewAtTag(nodeView, (blockInfo.height - 1).toString) { (tagStateView, blockContext) =>
      // enable tracing
      blockContext.enableTracer(config)

      // apply mainchain references
      for (mcBlockRefData <- block.mainchainBlockReferencesData) {
        tagStateView.applyMainchainBlockReferenceData(mcBlockRefData)
      }

      val gasPool = new GasPool(block.header.gasLimit)

      // apply all transaction, collecting traces on the way
      val evmResults = block.transactions.zipWithIndex.map({ case (tx, i) =>
        tagStateView.applyTransaction(tx, i, gasPool, blockContext)
        blockContext.getEvmResult
      })

      // return the list of tracer results from the evm
      val tracerResultList = new ListBuffer[JsonNode]
      for (evmResult <- evmResults) {
        if (evmResult != null && evmResult.tracerResult != null)
          tracerResultList += evmResult.tracerResult
      }
      tracerResultList.toList
    }
  }

  @RpcMethod("debug_traceBlockByNumber")
  @RpcOptionalParameters(1)
  def traceBlockByNumber(number: String, config: TraceOptions): List[JsonNode] = {
    applyOnAccountView { nodeView =>
      try {
        traceBlockById(nodeView, getBlockIdByTag(nodeView, number), config)
      } catch {
        case _: BlockNotFoundException => null
      }
    }
  }

  @RpcMethod("debug_traceBlockByHash")
  @RpcOptionalParameters(1)
  def traceBlockByHash(hash: Hash, config: TraceOptions): List[JsonNode] = {
    applyOnAccountView { nodeView =>
      try {
        traceBlockById(nodeView, bytesToId(hash.toBytes), config)
      } catch {
        case _: BlockNotFoundException => null
      }
    }
  }

  @RpcMethod("debug_traceTransaction")
  @RpcOptionalParameters(1)
  def traceTransaction(transactionHash: Hash, config: TraceOptions): JsonNode = {
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
          tagStateView.applyMainchainBlockReferenceData(mcBlockRefData)
        }

        val gasPool = new GasPool(block.header.gasLimit)

        // separate transactions within the block to the ones before the requested Tx and the rest
        val (previousTransactions, followingTransactions) = block.transactions.span(_.id != requestedTransactionHash)
        val requestedTx = followingTransactions.head

        // apply previous transactions without tracing
        for ((tx, i) <- previousTransactions.zipWithIndex) {
          tagStateView.applyTransaction(tx, i, gasPool, blockContext)
        }

        // enable tracing
        blockContext.enableTracer(config)

        // apply requested transaction with tracing enabled
        tagStateView.applyTransaction(requestedTx, previousTransactions.length, gasPool, blockContext)

        // return the tracer result from the evm
        blockContext.getEvmResult.tracerResult
      }
    }
  }

  @RpcMethod("debug_traceCall")
  @RpcOptionalParameters(1)
  def traceCall(params: TransactionArgs, tag: String, config: TraceOptions): JsonNode = {

    applyOnAccountView { nodeView =>
      // get block info
      val blockInfo = getBlockInfoById(nodeView, getBlockIdByHashOrTag(nodeView, tag))

      // get state at selected block
      getStateViewAtTag(nodeView, if (tag == "pending") "pending" else blockInfo.height.toString) {
        (tagStateView, blockContext) =>
          // enable tracing
          blockContext.enableTracer(config)

          // apply requested message with tracing enabled
          val msg = params.toMessage(blockContext.baseFee, settings.globalRpcGasCap)
          tagStateView.applyMessage(msg, new GasPool(msg.getGasLimit), blockContext)

          // return the tracer result from the evm
          blockContext.getEvmResult.tracerResult
      }
    }
  }

  @RpcMethod("zen_getForwardTransfers")
  def getForwardTransfers(blockHashOrNumber: String): ForwardTransfersView = {
    applyOnAccountView { nodeView =>
      nodeView.history
        .getStorageBlockById(getBlockIdByHashOrNumber(nodeView, blockHashOrNumber))
        .map(getForwardTransfersForBlock(_).asJava)
        .map(new ForwardTransfersView(_))
        .orNull
    }
  }

  @RpcMethod("zen_getFeePayments")
  def getFeePayments(blockHashOrNumber: String): FeePaymentsView = {
    applyOnAccountView { nodeView =>
      nodeView.history
        .feePaymentsInfo(getBlockIdByHashOrNumber(nodeView, blockHashOrNumber))
        .map(new FeePaymentsView(_))
        .orNull
    }
  }

  @RpcMethod("eth_getStorageAt")
  @RpcOptionalParameters(1)
  def getStorageAt(address: Address, key: BigInteger, tag: String): Hash = {
    val storageKey = BigIntegerUtil.toUint256Bytes(key)
    applyOnAccountView { nodeView =>
      getStateViewAtTag(nodeView, tag) { (stateView, _) =>
        new Hash(stateView.getAccountStorage(address, storageKey))
      }
    }
  }

  @RpcMethod("eth_getProof")
  @RpcOptionalParameters(1)
  def getProof(address: Address, keys: Array[BigInteger], tag: String): ProofAccountResult = {
    val storageKeys = keys.map(BigIntegerUtil.toUint256Bytes)
    applyOnAccountView { nodeView =>
      try {
        getStateViewAtTag(nodeView, tag) { (stateView, _) =>
          stateView.getProof(address, storageKeys)
        }
      } catch {
        case _: BlockNotFoundException => null
      }
    }
  }

  @RpcMethod("eth_accounts")
  def getAccounts: Array[Address] = {
    applyOnAccountView { nodeView =>
      nodeView.vault
        .secretsOfType(classOf[PrivateKeySecp256k1])
        .map(_.asInstanceOf[PrivateKeySecp256k1].publicImage().address())
        .toArray
    }
  }

  @RpcMethod("eth_feeHistory")
  @RpcOptionalParameters(1)
  def feeHistory(
      blockCount: BigInteger,
      newestBlock: String,
      rewardPercentiles: Array[Double]
  ): EthereumFeeHistoryView = {
    val percentiles = sanitizePercentiles(rewardPercentiles)
    applyOnAccountView { nodeView =>
      val (requestedBlock, requestedBlockInfo) = getBlockByTag(nodeView, newestBlock)
      // limit the range of blocks by the number of available blocks and cap at 1024
      val blocks = blockCount.intValueExact().min(requestedBlockInfo.height).min(1024)
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
          gasUsedRatio(i) = block.header.gasUsed.doubleValue() / block.header.gasLimit.doubleValue()
          if (percentiles.nonEmpty) reward(i) = Backend.getRewardsForBlock(block, stateView, percentiles)
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

  @RpcMethod("eth_getLogs")
  def getLogs(query: FilterQuery): Seq[EthereumLogView] = {
    applyOnAccountView { nodeView =>
      using(nodeView.state.getView) { stateView =>
        if (query.blockHash != null) {
          // we currently need to get the block by blockhash and then retrieve the receipt for each tx via tx-hash
          // geth retrieves all logs of a block by blockhash
          val block = nodeView.history
            .getStorageBlockById(bytesToId(query.blockHash.toBytes))
            .getOrElse(throw BlockNotFoundException())
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
  private def getBlockLogs(
      stateView: AccountStateView,
      block: AccountBlock,
      query: FilterQuery
  ): Seq[EthereumLogView] = {
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
   * Tests if a bloom filter matches the given address and topic queries. Replication of the original implementation in
   * GETH, see: github.com/ethereum/go-ethereum@v1.10.26/eth/filters/filter.go:328
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
   * Tests if a log matches the given address and topic queries. Replication of the original implementation in GETH,
   * see: github.com/ethereum/go-ethereum@v1.10.26/eth/filters/filter.go:293
   */
  private def testLog(addresses: Array[Address], topics: Array[Array[Hash]])(log: EthereumLogView): Boolean = {
    if (addresses.length > 0 && addresses.contains(log.address)) return false
    // skip if the number of filtered topics is greater than the amount of topics in the log
    if (topics.length > log.topics.length) return false
    topics.zip(log.topics).forall({ case (sub, topic) => sub.length == 0 || sub.contains(topic) })
  }
}
