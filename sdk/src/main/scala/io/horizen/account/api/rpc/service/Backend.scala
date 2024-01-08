package io.horizen.account.api.rpc.service

import io.horizen.account.block.AccountBlock
import io.horizen.account.history.AccountHistory
import io.horizen.account.state.AccountStateView
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.utils.FeeUtils.INITIAL_BASE_FEE
import io.horizen.utils.BytesUtils
import sparkz.util.{ModifierId, SparkzLogging}

import java.math.BigInteger

object Backend extends SparkzLogging {
//  private type NV = CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool]

  private val MAX_GAS_PRICE = INITIAL_BASE_FEE.multiply(BigInteger.valueOf(500))
  private val SUGGEST_TIP_TX_LIMIT = 3 //number of tx to consider per block for the tip estimation algorithm

  private[horizen] var tipCache : Option[TipCache] = Option.empty


  /**
   * Calculate suggested legacy gas price, i.e. including base fee.
   */
  def calculateGasPrice(history: AccountHistory, baseFee: BigInteger): BigInteger = {
    suggestTipCap(history).add(baseFee)
  }

  /**
   * Overload with default arguments.
   */
  def suggestTipCap(history: AccountHistory): BigInteger = suggestTipCap(history, 20, 20, MAX_GAS_PRICE, BigInteger.TWO)

  /**
   * Get tip cap that newly created transactions can use to have a high chance to be included in the following blocks.
   * Replication of the original implementation in GETH
   * NOTE: we use a different percentile than GETH (40 instead of 60) given low traffic conditions
   *
   * @see
   *   https://github.com/ethereum/go-ethereum/blob/v1.10.26/eth/gasprice/gasprice.go#L149
   * @see
   *   https://github.com/ethereum/go-ethereum/blob/v1.10.26/eth/ethconfig/config.go#L44
   *
   * @param history
   *   history to fetch blocks from
   * @param blockCount
   *   default 20
   * @param percentile
   *   default 20
   * @param maxPrice
   *   default 500 GWei
   * @param ignorePrice
   *   default 2 Wei
   * @return
   *   suggestion for maxPriorityFeePerGas
   */
  def suggestTipCap(
      history: AccountHistory,
      blockCount: Int = 20,
      percentile: Int = 20,
      maxPrice: BigInteger = MAX_GAS_PRICE,
      ignorePrice: BigInteger = BigInteger.TWO
  ): BigInteger = {
    var number = history.getCurrentHeight
    val headHash = history.bestBlockId
    val (lastHead : Option[ModifierId], lastPrice: BigInteger) = tipCache.fold(
      (Option.empty[ModifierId], BigInteger.ZERO)
    )(cache =>
      (Some(cache.blockHash), cache.value)
    )

    // If the latest gasprice is still valid, return it.
    if (lastHead.isDefined && lastHead.get == headHash){
      return lastPrice
    }

    var sent = 0
    var exp = 0
    var prices: Seq[Option[Seq[BigInteger]]] =  Seq() //in go-ethereum this is called result
    var results: Seq[BigInteger] = Seq()
    while (sent < blockCount && number > 0){
      prices = prices :+ getBlockPrices(history, number, ignorePrice, SUGGEST_TIP_TX_LIMIT)
      sent += 1
      exp += 1
      number -= 1
    }
    while (exp > 0){
      val pricesOfABlock : Option[Seq[BigInteger]] = prices.head
      prices = prices.drop(1)
      if (pricesOfABlock.isEmpty) {
        //if we are here we had some errors collecting the blocks
        log.warn("Error retrieving blocks in history while suggesting tip cap, returning the cached one")
        return lastPrice
      }
      exp -= 1
      var res : Seq[BigInteger] = pricesOfABlock.get
      if (res.isEmpty) {
        // Nothing returned. There are two special cases here:
        // - The block is empty
        // - All the transactions included are sent by the miner itself.
        // In these cases, use the latest calculated price for sampling.
        res = Seq(lastPrice)
      }

      // Besides, in order to collect enough data for sampling, if nothing
      // meaningful returned, try to query more blocks. But the maximum
      // is 2*checkBlocks.
      if (res.length == 1 && results.length + 1 + exp < blockCount * 2 && number > 0) {
        prices = prices :+ getBlockPrices(history, number, ignorePrice, SUGGEST_TIP_TX_LIMIT)
        exp += 1
        number -= 1
      }
      results = results ++ res
    }

    var resultPrice = lastPrice
    if (!results.isEmpty) {
      resultPrice =
        results
          .sorted
          .lift((results.length - 1) * percentile / 100)
          .getOrElse(lastPrice)
          .min(maxPrice)
    }

    //cache the result for next calls
    tipCache = Some(TipCache(headHash, resultPrice))
    resultPrice
  }

  /**
   * Calculates the lowest transaction gas price in a given block
   * If the block is empty or all transactions are sent by the miner itself, empty sequence is returned.
   * If we are unable to find the block, return an empty option.
   * Replication of the original implementation in GETH, see:
   * https://github.com/ethereum/go-ethereum/blob/v1.10.26/eth/gasprice/gasprice.go#L257
   */
  private def getBlockPrices(history: AccountHistory,
                             blockHeight: Int,
                             ignoreUnder: BigInteger,
                             limit: Int): Option[Seq[BigInteger]] = {

    val blockId = history.blockIdByHeight(blockHeight)
    if (blockId.isEmpty){
      return Option.empty
    }
    val blockOpt = history.getStorageBlockById(ModifierId(blockId.get))
    if (blockOpt.isEmpty){
      return Option.empty
    }
    val block = blockOpt.get
    Some(block.transactions
      .filter(tx => !(tx.getFrom.bytes() sameElements block.forgerPublicKey.bytes()))
      .map(tx => getEffectiveGasTip(tx.asInstanceOf[EthereumTransaction], block.header.baseFee))
      .filter(gasTip => ignoreUnder == null || gasTip.compareTo(ignoreUnder) >= 0)
      .sorted
      .take(limit))
  }

  def getRewardsForBlock(
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
}

case class TipCache(blockHash: ModifierId,  value: BigInteger)

