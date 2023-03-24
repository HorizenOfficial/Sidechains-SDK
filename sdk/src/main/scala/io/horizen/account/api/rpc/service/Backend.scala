package io.horizen.account.api.rpc.service

import io.horizen.account.block.AccountBlock
import io.horizen.account.history.AccountHistory
import io.horizen.account.state.AccountStateView
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.utils.FeeUtils.INITIAL_BASE_FEE
import io.horizen.utils.BytesUtils
import sparkz.util.ModifierId

import java.math.BigInteger

object Backend {
//  private type NV = CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool]

  private val MAX_GAS_PRICE = INITIAL_BASE_FEE.multiply(BigInteger.valueOf(500))

  /**
   * Calculate suggested legacy gas price, i.e. including base fee.
   */
  def calculateGasPrice(history: AccountHistory, baseFee: BigInteger): BigInteger = {
    suggestTipCap(history).add(baseFee).min(MAX_GAS_PRICE)
  }

  /**
   * Overload with default arguments.
   */
  def suggestTipCap(history: AccountHistory): BigInteger = suggestTipCap(history, 20, 60, MAX_GAS_PRICE, BigInteger.TWO)

  /**
   * Get tip cap that newly created transactions can use to have a high chance to be included in the following blocks.
   * Replication of the original implementation in GETH w/o caching, see:
   * github.com/ethereum/go-ethereum/blob/master/eth/gasprice/gasprice.go#L150
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
   *   default 60
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
      percentile: Int = 60,
      maxPrice: BigInteger = MAX_GAS_PRICE,
      ignorePrice: BigInteger = BigInteger.TWO
  ): BigInteger = {
    val blockHeight = history.getCurrentHeight
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
        val block = history
          .blockIdByHeight(blockHeight - i)
          .map(ModifierId(_))
          .flatMap(history.getStorageBlockById)
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
   * Calculates the lowest transaction gas price in a given block If the block is empty or all transactions are sent by
   * the miner itself, empty sequence is returned. Replication of the original implementation in GETH, see:
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
