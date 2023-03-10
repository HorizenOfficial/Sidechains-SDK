package io.horizen.account.api.rpc.service

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.fasterxml.jackson.databind.JsonNode
import io.horizen.EthServiceSettings
import io.horizen.account.api.rpc.handler.RpcException
import io.horizen.account.api.rpc.types._
import io.horizen.account.api.rpc.utils._
import io.horizen.account.block.AccountBlock
import io.horizen.account.companion.SidechainAccountTransactionsCompanion
import io.horizen.account.forger.AccountForgeMessageBuilder
import io.horizen.account.history.AccountHistory
import io.horizen.account.mempool.AccountMemoryPool
import io.horizen.account.proof.SignatureSecp256k1
import io.horizen.account.secret.PrivateKeySecp256k1
import io.horizen.account.state._
import io.horizen.account.state.receipt.EthereumReceipt
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.utils.AccountForwardTransfersHelper.getForwardTransfersForBlock
import io.horizen.account.utils.FeeUtils.{INITIAL_BASE_FEE, calculateNextBaseFee}
import io.horizen.account.utils.Secp256k1.generateContractAddress
import io.horizen.account.utils._
import io.horizen.account.wallet.AccountWallet
import io.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import io.horizen.chain.SidechainBlockInfo
import io.horizen.evm.results.ProofAccountResult
import io.horizen.evm.{Address, Hash, TraceOptions}
import io.horizen.forge.MainchainSynchronizer
import io.horizen.network.SyncStatus
import io.horizen.params.NetworkParams
import io.horizen.transaction.exception.TransactionSemanticValidityException
import io.horizen.utils.{BytesUtils, ClosableResourceHandler, TimeToEpochUtils}
import io.horizen.network.SyncStatusActor.ReceivableMessages.GetSyncStatus
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
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future}
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
    syncStatusActorRef: ActorRef,
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

  @RpcMethod("txpool_content")
  def txpoolContent(): TxPoolContent = applyOnAccountView { nodeView =>
    new TxPoolContent(
      nodeView.pool.getExecutableTransactionsMap,
      nodeView.pool.getNonExecutableTransactionsMap
    )
  }

  @RpcMethod("txpool_contentFrom")
  def txpoolContentFrom(from: Address): TxPoolContentFrom = applyOnAccountView { nodeView =>
    new TxPoolContentFrom(
      nodeView.pool.getExecutableTransactionsMapFrom(from),
      nodeView.pool.getNonExecutableTransactionsMapFrom(from)
    )
  }

  @RpcMethod("txpool_inspect")
  def txpoolInspect(): TxPoolInspect = applyOnAccountView { nodeView =>
    new TxPoolInspect(
      nodeView.pool.getExecutableTransactionsMapInspect,
      nodeView.pool.getNonExecutableTransactionsMapInspect)
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
    val unsignedTx = params.toTransaction(networkParams)
    applyOnAccountView { nodeView =>
      val secret = getFittingSecret(nodeView.vault, nodeView.state, Option.apply(params.from), unsignedTx.maxCost())
      val tx = signTransactionWithSecret(secret, unsignedTx)
      tx.encode(tx.isSigned)
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
      val secret = getFittingSecret(nodeView.vault, nodeView.state, Some(sender), BigInteger.ZERO)
      val signature = secret.sign(messageToSign)
      signature.getR ++ signature.getS ++ signature.getV
    }
  }

  private def getFittingSecret(
      wallet: AccountWallet,
      state: AccountState,
      sender: Option[Address],
      minimumBalance: BigInteger
  ): PrivateKeySecp256k1 = {
    val matchingKeys = wallet
      .secretsOfType(classOf[PrivateKeySecp256k1])
      .map(_.asInstanceOf[PrivateKeySecp256k1])
      // if from address is given the secrets public key needs to match, otherwise check all of the secrets
      .filter(secret => sender.forall(_.equals(secret.publicImage.address)))
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

  @RpcMethod("eth_estimateGas")
  @RpcOptionalParameters(1)
  def estimateGas(params: TransactionArgs, tag: String): BigInteger = {
    applyOnAccountView { nodeView =>
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
      calculateGasPrice(nodeView, nodeView.history.bestBlock.header.baseFee)
    }
  }

  /**
   * DefaultMaxPrice = 500 GWei, DefaultIgnorePrice = 2 Wei
   * https://github.com/ethereum/go-ethereum/blob/master/eth/gasprice/gasprice.go
   * FullNode Defaults: Blocks = 20, Percentile = 60
   * LightClient Defaults: Blocks = 2, Percentile = 60
   * https://github.com/ethereum/go-ethereum/blob/master/eth/ethconfig/config.go
   */
  private def calculateGasPrice(nodeView: NV, baseFee: BigInteger): BigInteger = {
    val nrOfBlocks = 20
    val percentile = 60
    val maxPrice = INITIAL_BASE_FEE.multiply(BigInteger.valueOf(500))
    val ignorePrice = BigInteger.TWO
    suggestTipCap(nodeView, nrOfBlocks, percentile, maxPrice, ignorePrice).add(baseFee).min(maxPrice)
  }

  /**
   * Get tip cap that newly created transactions can use to have a high chance to be included in the following blocks.
   * Replication of the original implementation in GETH w/o caching, see:
   * github.com/ethereum/go-ethereum/blob/master/eth/gasprice/gasprice.go#L150
   */
  private def suggestTipCap(
      nodeView: NV,
      blockCount: Int,
      percentile: Int,
      maxPrice: BigInteger,
      ignorePrice: BigInteger
  ): BigInteger = {
    val blockHeight = nodeView.history.getCurrentHeight
    // limit the range of blocks by the number of available blocks and cap at 1024
    val blocks: Integer = (blockCount * 2).min(blockHeight).min(1024)

    // define limit for included gas prices each block
    val limit = 3
    val prices: Seq[BigInteger] = {
      var collected = 0
      var moreBlocksNeeded = false
      // Return lowest tx gas prices of each requested block, sorted in ascending order.
      // Queries up to 2*blockCount blocks, but stops in range > blockCount if enough samples were found.
      (0 until blocks).withFilter(_ => !moreBlocksNeeded || collected < 2).map { i =>
        val block = nodeView.history
          .blockIdByHeight(blockHeight - i)
          .map(ModifierId(_))
          .flatMap(nodeView.history.getStorageBlockById)
          .get
        val blockPrices = getBlockPrices(block, ignorePrice, limit)
        collected += blockPrices.length
        if (i >= blockCount) moreBlocksNeeded = true
        blockPrices
      }
    }.flatten

    prices
      .sorted
      .lift((prices.length - 1) * percentile / 100)
      .getOrElse(BigInteger.ZERO)
      .min(maxPrice)
  }

  /**
   * Calculates the lowest transaction gas price in a given block
   * If the block is empty or all transactions are sent by the miner itself, empty sequence is returned.
   * Replication of the original implementation in GETH, see:
   * github.com/ethereum/go-ethereum/blob/master/eth/gasprice/gasprice.go#L258
   */
  private def getBlockPrices(block: AccountBlock, ignoreUnder: BigInteger, limit: Int): Seq[BigInteger] = {
    block.transactions
      .filter(tx => !(tx.getFrom.bytes() sameElements block.forgerPublicKey.bytes()))
      .map(tx => getEffectiveGasTip(tx.asInstanceOf[EthereumTransaction], block.header.baseFee))
      .filter(gasTip => ignoreUnder == null || gasTip.compareTo(ignoreUnder) >= 0)
      .sorted
      .take(limit)
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
  def eth_syncing(): Any = {
    implicit val timeout: Timeout = new Timeout(nvtimeout)
    Try {
      Await.result(syncStatusActorRef ? GetSyncStatus, nvtimeout).asInstanceOf[SyncStatus]
    } match {
      case Success(syncStatus: SyncStatus) =>
        if (!syncStatus.syncStatus) false
        else syncStatus
      case Failure(e) =>
        throw new RpcException(RpcError.fromCode(
          RpcCode.InternalError,
          s"error during eth_syncing call: ${e.getMessage}"
        ))
    }
  }

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
          stateView.getTransactionReceipt(BytesUtils.fromHexString(tx.id)).get.gasUsed.longValueExact(),
          getEffectiveGasTip(tx, block.header.baseFee)
        )
      )
      .sortBy(_.reward)
      .iterator

    var current = sortedRewards.next()
    var sumGasUsed = current.gasUsed
    val rewards = new Array[BigInteger](percentiles.length)
    for (i <- percentiles.indices) {
      val thresholdGasUsed = (block.header.gasUsed.doubleValue() * percentiles(i) / 100).toLong
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
    applyOnAccountView { nodeView =>
      using(nodeView.state.getView) { stateView =>
        if (query.blockHash != null) {
          // we currently need to get the block by blockhash and then retrieve the receipt for each tx via tx-hash
          // geth retrieves all logs of a block by blockhash
          val block = nodeView.history
            .getStorageBlockById(bytesToId(query.blockHash.toBytes))
            .getOrElse(throw new RpcException(RpcError.fromCode(RpcCode.UnknownBlock, "invalid block hash")))
          RpcFilter.getBlockLogs(stateView, block, query)
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
              .map(RpcFilter.getBlockLogs(stateView, _, query))
              .get
          })
        }
      }
    }
  }
}
